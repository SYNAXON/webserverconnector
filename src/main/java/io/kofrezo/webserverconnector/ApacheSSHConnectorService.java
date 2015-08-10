package io.kofrezo.webserverconnector;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.kofrezo.webserverconnector.interfaces.WebserverConnectorService;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Apache SSH Webserver Connector Implementation
 *
 * Concrete implementation of webserver connector to setup apache webserver via SSH connection.
 *
 * @author Daniel Kröger <daniel.kroeger@kofrezo.io>
 * @version 08.08.2015
 */
@Named
@ApplicationScoped
public class ApacheSSHConnectorService implements WebserverConnectorService, Serializable {

    private static final Logger LOGGER = LogManager.getLogger(ApacheSSHConnectorService.class);

    private String user;
    private String host;
    private int port;
    private String publickey;
    private String privatekey;
    private String passphrase;

    private String template;

    /**
     * Execute Command Via SSH
     *
     * Executes a command via ssh on the remote server.
     *
     * @param command
     * @return result from stdout
     */
    protected String execute(String command) {
        if (this.isValid()) {
            try {
                JSch jsch = new JSch();
                jsch.addIdentity(this.privatekey, this.publickey, this.passphrase.getBytes());

                Session session = jsch.getSession(this.user, this.host, this.port);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();

                ChannelExec channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(command);
                channel.setErrStream(System.err);
                InputStream in = channel.getInputStream();
                channel.connect();

                byte[] data = new byte[1024];
                StringBuilder stdout = new StringBuilder();
                while (true) {
                    while (in.available() > 0) {
                        int read = in.read(data, 0, 1024);
                        if (read < 0) {
                            break;
                        }
                        stdout.append(new String(data, 0, read));
                    }
                    if (channel.isClosed()) {
                        if (in.available() > 0) {
                            continue;
                        }
                        break;
                    }
                    Thread.sleep(500); // I absolutely don't of this is too much or less
                }
                int code = channel.getExitStatus();

                LOGGER.debug("command: " + command + " exit code: " + code + " message: " + stdout);

                channel.disconnect();
                session.disconnect();
                return stdout.toString();
            } catch (JSchException | IOException | InterruptedException ex) {
                LOGGER.error(ex.getMessage(), ex);
            }
        }

        return "";
    }

    @Override
    public boolean isValid() {
        if (this.user != null && this.host != null && this.port > 0 && this.port < 65537 && this.passphrase != null) {
            File pubKey = new File(this.publickey);
            File privKey = new File(this.privatekey);
            if (pubKey.canRead() && privKey.canRead()) {
                return true;
            }
        }

        LOGGER.debug("connector not ready - missing or wrong credentials for user, host, port, keys or passphrase");

        return false;
    }

    @Override
    public String[] getVirtualHosts() {
        String stdout = this.execute("ls /etc/apache2/sites-available");
        return stdout.split("\n");
    }

    @Override
    public String[] getVirtualHostsEnabled() {
        String stdout = this.execute("ls /etc/apache2/sites-enabled");
        return stdout.split("\n");
    }

    @Override
    public String[] getVirtualHostsDisabled() {
        String[] available = this.getVirtualHosts();
        String[] enabled = this.getVirtualHostsEnabled();
        ArrayList disabled = new ArrayList();

        for (String currentAvailable : available) {
            for (String currentEnabled : enabled) {
                if (currentAvailable.equals(currentEnabled)) {
                    disabled.add(currentEnabled);
                }
            }
        }

        String[] result = new String[disabled.size()];
        return (String[]) disabled.toArray(result);
    }

    @Override
    public void createVirtualHost(String domain, String[] aliases) {
        String vhost = this.template.replaceAll("%DOMAIN%", domain);
        String command1 = "echo '" + vhost + "' >> /tmp/" + domain;
        String command2 = "sudo mv /tmp/" + domain + " /etc/apache2/sites-available/";
        
        this.execute(command1);
        this.execute(command2);
    }

    @Override
    public void deleteVirtualHost(String domain) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void enableVirtualHost(String domain) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void disableVirtualHost(String domain) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setVirtualHostTemplate(String filename) {
        File file = new File(filename);
        if (file.canRead()) {
            try {
                FileInputStream fis = new FileInputStream(file);

                byte[] data = new byte[1024];
                StringBuilder result = new StringBuilder();
                while (fis.available() > 0) {
                    int read = fis.read(data, 0, 1024);
                    result.append(new String(data, 0, read));
                }

                this.template = result.toString();
            } catch (IOException ex) {
                LOGGER.error(ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void setAuthenticationCredentials(HashMap<String, String> credentials) {
        if (credentials.containsKey("user")) {
            this.user = credentials.get("user");
        }
        if (credentials.containsKey("host")) {
            this.host = credentials.get("host");
        }
        if (credentials.containsKey("port")) {
            this.port = Integer.parseInt(credentials.get("port"));
        }
        if (credentials.containsKey("publickey")) {
            this.publickey = credentials.get("publickey");
        }
        if (credentials.containsKey("privatekey")) {
            this.privatekey = credentials.get("privatekey");
        }
        if (credentials.containsKey("passphrase")) {
            this.passphrase = credentials.get("passphrase");
        }
    }
}
