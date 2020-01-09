/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2002 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Axis" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package extensions.wsrm;

import org.apache.axis.MessageContext;
import org.apache.axis.AxisFault;
import org.apache.axis.client.Call;
import org.apache.axis.message.SOAPEnvelope;
import org.apache.axis.message.SOAPHeaderElement;
import org.apache.axis.message.PrefixedQName;
import org.apache.axis.message.MessageElement;
import org.apache.axis.handlers.BasicHandler;

import javax.xml.namespace.QName;
import java.util.*;

public class AckService extends BasicHandler implements RMConstants {
    static RetryGuy myRetryGuy = null;

    /**
     * Wake up every three seconds, and scan all active sequences to see
     * if any messages need to be resent (no ack in 20 seconds).
     */ 
    class RetryGuy implements Runnable {
        public void run() {
            while (true) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Iterator i = sequences.values().iterator();
                while (i.hasNext()) {
                    MySequence seq = (MySequence)i.next();
                    synchronized (seq.activeMessages) {
                        Iterator msgs = seq.activeMessages.iterator();
                        while (msgs.hasNext()) {
                            MessageRecord msg = (MessageRecord)msgs.next();
                            if (msg.timestamp < (new Date().getTime() - 20000)) {
                                try {
                                    resendMsg(msg, seq.destination);
                                } catch (Exception e) {
                                    e.printStackTrace();  //To change body of catch statement use Options | File Templates.
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    class MySequence {
        String id;
        String destination;

        int currentMsgNum = 1;

        // My queue of active messages, indexed by sequence number
        LinkedList activeMessages = new LinkedList();
    }

    class MessageRecord {
        int sequenceNumber;
        long timestamp = new Date().getTime();
        SOAPEnvelope env;
    }

    void resendMsg(MessageRecord msg, String destination) throws Exception {
        System.out.println("Resending : dest = " + destination + ", msg " + msg.sequenceNumber);
        
        Call call = new Call(destination);
        call.setProperty("OneWay", Boolean.TRUE);
        call.invoke(msg.env);
        msg.timestamp = new Date().getTime();
    }

    Map sequences = new HashMap();

    public void invoke(MessageContext msgContext) throws AxisFault {
        processAck(msgContext.getRequestMessage().getSOAPEnvelope());
    }

    public void processAck(SOAPEnvelope req) throws AxisFault
    {
        SOAPHeaderElement header = req.getHeaderByName(NS_URI_WSRM, "SequenceAcknowledgement");
        if (header == null)
            return;  // Fault?

        Iterator i = header.getChildElements(new PrefixedQName(NS_URI_WSU, "Identifier", null));
        if (!i.hasNext()) {
            // return fault
            throw new AxisFault("WSRM.Fault", "Missing identifier in Sequence", null, null);
        }

        MessageElement el = (MessageElement)i.next();
        String id = el.getValue();
        MySequence seq = (MySequence)sequences.get(id);
        if (seq == null) {
            // Acknowledging a sequence I don't know about...
            throw new AxisFault("WSRM.UnknownSequence", "Don't recognize ack of sequence '" + id + "'", null, null);
        }

        i = header.getChildElements(new PrefixedQName(NS_URI_WSRM, "AcknowledgementRange", null));
        while (i.hasNext()) {
            el = (MessageElement)i.next();
            String val = el.getAttributeValue("Upper");
            int upper = Integer.parseInt(val);
            val = el.getAttributeValue("Lower");
            int lower = Integer.parseInt(val);
            acknowledgeRange(seq, lower, upper);
        }

        header.setProcessed(true);

        header = req.getHeaderByName(NS_URI_WSA, "From");
        if (header != null) {
            header.setProcessed(true);
        }

        header = req.getHeaderByName(NS_URI_WSA, "To");
        if (header != null) {
            header.setProcessed(true);
        }

        header = req.getHeaderByName(NS_URI_WSA, "MessageID");
        if (header != null) {
            header.setProcessed(true);
        }

        header = req.getHeaderByName(NS_URI_WSA, "Action");
        if (header != null) {
            header.setProcessed(true);
        }
    }

    /**
     * Process an acknowledgement range.  Remove every matching message from
     * our resend queue.
     * 
     * @param seq the sequence we're dealing with
     * @param min the lowest messageID that has been ack'ed
     * @param max the highest messageID that has been ack'ed
     */ 
    public void acknowledgeRange(MySequence seq, int min, int max) {
        LinkedList activeMessages = seq.activeMessages;
        synchronized (activeMessages) {
            for (int i = 0; i < activeMessages.size(); i++) {
                MessageRecord curMsg = (MessageRecord)activeMessages.get(i);
                if (min <= curMsg.sequenceNumber && max >= curMsg.sequenceNumber) {
                    System.out.println("Removed msg #" + curMsg.sequenceNumber);
                    activeMessages.remove(i);
                    i--;
                }
            }
        }
    }

    /**
     * Take a SOAP envelope that we want to send in a particular sequence,
     * and decorate it appropriately with all the WS-RM headers.
     * 
     * @param env the envelope
     * @param toAddr the destination address
     * @param fromAddr the origination address (can be anonymous - see spec)
     * @param identifier the sequence identifier
     * @param isLast true if this is the last message in the sequence
     * @param skip cheesy extra parameter to tell the engine to skip a sequence
     *             number for testing purposes
     * @throws Exception
     */ 
    public void doit(SOAPEnvelope env,
                     String toAddr,
                     String fromAddr,
                     String identifier,
                     boolean isLast,
                     boolean skip) throws Exception {
        MySequence myseq = (MySequence)sequences.get(identifier);
        if (myseq == null) {
            // New one
            myseq = new MySequence();
            myseq.id = identifier;
            myseq.destination = toAddr;
            sequences.put(identifier, myseq);
        }

        int curMsgNum = myseq.currentMsgNum++;
        if (skip) {
            curMsgNum = myseq.currentMsgNum++;
        }
        Integer seq = new Integer(curMsgNum);
        String myAddr = fromAddr;

        SOAPHeaderElement header =
                new SOAPHeaderElement(SEQUENCE_QNAME.getNamespaceURI(),
                                      SEQUENCE_QNAME.getLocalPart());
        MessageElement el;

        el = new MessageElement(IDENTIFIER_QNAME, identifier);
        header.addChild(el);

        el = new MessageElement(MSGNUM_QNAME, seq);
        header.addChild(el);
        if (isLast) {
            el = new MessageElement(NS_URI_WSRM, "LastMessage");
            header.addChild(el);
        }

        env.addHeader(header);

        header = new SOAPHeaderElement(NS_URI_WSA, "Action", PING_URI);
        header.setMustUnderstand(true);
        env.addHeader(header);

        header = new SOAPHeaderElement(NS_URI_WSA, "From");
        header.setMustUnderstand(true);
        el = new MessageElement(new QName(NS_URI_WSA, "Address"), myAddr);
        header.addChild(el);
        env.addHeader(header);

        header = new SOAPHeaderElement(NS_URI_WSA, "To", toAddr);
        header.setMustUnderstand(true);
        env.addHeader(header);

        header = new SOAPHeaderElement(NS_URI_WSA, "MessageID", ReliableMessagingHandler.generateNewMsgID());
        header.setMustUnderstand(true);
        env.addHeader(header);

        MessageRecord msgRec = new MessageRecord();
        msgRec.env = env;
        msgRec.sequenceNumber = curMsgNum;
        myseq.activeMessages.add(msgRec);

        // Tack on any piggybacked acks I have for this guy...
        Iterator seqs = ReliableMessagingHandler.sequences.values().iterator();
        while (seqs.hasNext()) {
            Sequence s = (Sequence)seqs.next();
            if (s.endpoint.equals(toAddr)) {
                ReliableMessagingHandler.generateAck(env, s);
            }
        }

        synchronized (this) {
            if (myRetryGuy == null) {
                myRetryGuy = new RetryGuy();
                new Thread(myRetryGuy).start();
            }
        }
    }
}
