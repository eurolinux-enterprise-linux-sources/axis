package extensions.wsrm;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Glen
 * Date: Oct 14, 2003
 * Time: 8:39:21 PM
 * To change this template use Options | File Templates.
 */
public class Sequence {
    String id;
    int maxReceived;
    List missing = new ArrayList();
    String endpoint;

    public Sequence(String id) {
        this.id = id;
    }
}
