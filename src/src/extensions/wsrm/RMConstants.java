package extensions.wsrm;

import javax.xml.namespace.QName;

/**
 * Created by IntelliJ IDEA.
 * User: Glen
 * Date: Oct 14, 2003
 * Time: 10:59:52 PM
 * To change this template use Options | File Templates.
 */
public interface RMConstants {
    public static final String NS_URI_WSRM =
            "http://schemas.xmlsoap.org/ws/2003/03/rm";
    public static final String NS_URI_WSU =
            "http://schemas.xmlsoap.org/ws/2002/07/utility";
    public static final String NS_URI_WSP =
            "http://schemas.xmlsoap.org/ws/2002/12/policy";
    public static final String NS_URI_WSA =
            "http://schemas.xmlsoap.org/ws/2003/03/addressing";

    public static final QName SEQUENCE_QNAME =
            new QName(NS_URI_WSRM, "Sequence");
    public static final QName MSGNUM_QNAME =
            new QName(NS_URI_WSRM, "MessageNumber");
    public static final QName IDENTIFIER_QNAME =
            new QName(NS_URI_WSU, "Identifier");
    public static final String PING_URI = "urn:wsrm:Ping";

    public String URI_ANONYMOUS = "http://schemas.xmlsoap.org/ws/2003/03/addressing/role/anonymous";    

    public static final String SEQID = "WSRM.SequenceIdentifier";

    public static final String URI_ACK =
            "http://schemas.xmlsoap.org/ws/2003/03/rm#SequenceAcknowledgement";
}
