package extensions.wsrm;

import org.apache.axis.client.Call;
import org.apache.axis.message.SOAPEnvelope;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Glen
 * Date: Oct 14, 2003
 * Time: 10:18:53 PM
 * To change this template use Options | File Templates.
 */
public class AckTask implements Runnable {
    static AckTask singleton = null;
    public static synchronized AckTask getSingleton() {
        if (singleton == null) {
            singleton = new AckTask();
            // Start the thread
            new Thread(singleton).start();
        }
        return singleton;
    }

    List ackQueue = new ArrayList();

    public void scheduleAck(Sequence seq) {
        System.out.println("Acking to " + seq.endpoint);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use Options | File Templates.
        }
        synchronized (ackQueue) {
            ackQueue.add(seq);
            ackQueue.notifyAll();
        }
    }

    public void run() {
        while (true) {
            synchronized (ackQueue) {
                while (ackQueue.isEmpty()) {
                    try {
                        ackQueue.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();  //To change body of catch statement use Options | File Templates.
                    }
                }
            }

            while (!ackQueue.isEmpty()) {
                Sequence seq = (Sequence)ackQueue.get(0);
                synchronized (ackQueue) {
                    ackQueue.remove(0);
                }

                try {
                    generateAck(seq);
                } catch (Exception e) {
                    System.out.println("Couldn't generate ack!");
                    e.printStackTrace();
                }
            }
        }
    }

    public void generateAck(Sequence seq) throws Exception {
        Call call = new Call(seq.endpoint);
        SOAPEnvelope env = new SOAPEnvelope();
        ReliableMessagingHandler.generateAck(env, seq);
        call.setProperty("OneWay", Boolean.TRUE);
        call.invoke(env);
    }
}
