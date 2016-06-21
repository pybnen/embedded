package pervasive.jku.at.wifisensor.comm;

import org.fusesource.hawtbuf.Buffer;

public interface CommunicationListener {

    void messageReceived(String topic, Buffer content);
}
