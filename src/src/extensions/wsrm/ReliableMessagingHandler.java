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

import org.apache.axis.handlers.BasicHandler;
import org.apache.axis.MessageContext;
import org.apache.axis.AxisFault;
import org.apache.axis.Message;
import org.apache.axis.message.SOAPHeaderElement;
import org.apache.axis.message.MessageElement;
import org.apache.axis.message.SOAPEnvelope;
import org.apache.axis.message.PrefixedQName;

import javax.xml.soap.SOAPException;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;

public class ReliableMessagingHandler extends BasicHandler implements RMConstants {
    static Map sequences = new HashMap();

    public ReliableMessagingHandler() {
    }

    /**
     * Invoke is called to do the actual work of the Handler object.
     * If there is a fault during the processing of this method it is
     * invoke's job to catch the exception and undo any partial work
     * that has been completed.  Once we leave 'invoke' if a fault
     * is thrown, this classes 'onFault' method will be called.
     * Invoke should rethrow any exceptions it catches, wrapped in
     * an AxisFault.
     */
    public void invoke(MessageContext msgContext) throws AxisFault {
        SOAPEnvelope req = msgContext.getRequestMessage().getSOAPEnvelope();
        SOAPHeaderElement header = req.getHeaderByName(NS_URI_WSRM, "Sequence");
        if (header == null)
            return;

        Iterator i = header.getChildElements(new PrefixedQName(NS_URI_WSU, "Identifier", null));
        if (!i.hasNext()) {
            // return fault
            throw new AxisFault("WSRM.Fault", "Missing identifier in Sequence", null, null);
        }

        MessageElement el = (MessageElement)i.next();
        String id = el.getValue();
        Sequence seq = (Sequence)sequences.get(id);
        if (seq == null) {
            seq = new Sequence(id);
            sequences.put(id, seq);
        }

        i = header.getChildElements(new PrefixedQName(NS_URI_WSRM, "MessageNumber", null));
        el = (MessageElement)i.next();

        // We've received a message with a given ID.
        int seqNum = Integer.parseInt(el.getValue());
        int nextSeq = seq.maxReceived + 1;
        if (seqNum < nextSeq) {
            Integer s = new Integer(seqNum);
            if (seq.missing.contains(s)) {
                seq.missing.remove(s);
            }
        } else {
            if (seqNum > nextSeq) {
                // Missing everything between maxReceived and this
                for (int n = nextSeq; n < seqNum; n++) {
                    seq.missing.add(new Integer(n));
                }
            }
            seq.maxReceived = seqNum;
        }

        header.setProcessed(true);

        String from = null;
        header = req.getHeaderByName(NS_URI_WSA, "From");
        if (header != null) {
            i = header.getChildElements(new PrefixedQName(NS_URI_WSA, "Address", null));
            if (!i.hasNext()) {
                throw new AxisFault("WSRM.NoAddress", "No <Address> element in <From> header", null, null);
            }
            el = (MessageElement)i.next();
            from = el.getValue();
            header.setProcessed(true);
        }

        msgContext.setProperty("wsrm.From", from);

        // Always schedule an async ack if there's a valid From
        boolean doSynchronousAcks = (URI_ANONYMOUS.equals(from));

        if (seq.endpoint == null)
            seq.endpoint = from;

        if (!doSynchronousAcks) {
            AckTask.getSingleton().scheduleAck(seq);
        } else {
            Message respMsg = msgContext.getResponseMessage();
            if (respMsg == null) {
                respMsg = new Message(new SOAPEnvelope());
                msgContext.setResponseMessage(respMsg);
            }
            SOAPEnvelope env = respMsg.getSOAPEnvelope();
            try {
                generateAck(env, seq);
            } catch (SOAPException e) {
                e.printStackTrace();  //To change body of catch statement use Options | File Templates.
            }
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

    public static void generateAck(SOAPEnvelope env, Sequence seq) throws SOAPException {
        SOAPHeaderElement seqHeader = new SOAPHeaderElement(NS_URI_WSRM, "SequenceAcknowledgement");
        seqHeader.setMustUnderstand(true);
        MessageElement el = new MessageElement(IDENTIFIER_QNAME, seq.id);
        seqHeader.addChild(el);

        Iterator i = seq.missing.iterator();
        int lastMissing = 1;
        while (i.hasNext()) {
            int id = ((Integer)i.next()).intValue();
            ackRange(lastMissing, id - 1, seqHeader);
            lastMissing = id + 1;
        }
        ackRange(lastMissing, seq.maxReceived, seqHeader);

        env.addHeader(seqHeader);
        SOAPHeaderElement actionHeader = new SOAPHeaderElement(NS_URI_WSA, "Action", URI_ACK);
        actionHeader.setMustUnderstand(true);
        env.addHeader(actionHeader);

        SOAPHeaderElement toHeader = new SOAPHeaderElement(NS_URI_WSA, "To", seq.endpoint);
        env.addHeader(toHeader);

        SOAPHeaderElement idHeader = new SOAPHeaderElement(NS_URI_WSA, "MessageID", generateNewMsgID());
        idHeader.setMustUnderstand(true);
        env.addHeader(idHeader);
    }

    public static void ackRange(int start, int end, SOAPHeaderElement seqHeader) throws SOAPException {
        if (start > end) return;

        MessageElement rangeEl = new MessageElement(NS_URI_WSRM, "AcknowledgementRange");
        rangeEl.addAttribute("", "Upper", String.valueOf(end));
        rangeEl.addAttribute("", "Lower", String.valueOf(start));
        seqHeader.addChild(rangeEl);
    }

    public static synchronized String generateNewMsgID() {
        return "urn:messageID-" + new Date().getTime();
    }

    public static synchronized String generateNewIdentifier() {
        return "urn:identifier-" + new Date().getTime();
    }

}
