package io.kofrezo.webserverconnector;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.kofrezo.webserverconnector.interfaces.WebserverConnectorService;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
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
 * @created Aug 8, 2015
 */
@Named
@RequestScoped
public class ApacheSSHConnectorService implements WebserverConnectorService, Serializable {

    private static final Logger LOGGER = LogManager.getLogger(ApacheSSHConnectorService.class);

    private String[] available;
    private String[] enabled;
    private String[] disabled;

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
        if (this.available == null) {
            String stdout = this.execute("ls /etc/apache2/sites-available");
            this.available = stdout.split("\n");
        }
        return this.available;
    }

    @Override
    public String[] getVirtualHostsEnabled() {
        if (this.enabled == null) {
            String stdout = this.execute("ls /etc/apache2/sites-enabled");
            this.enabled = stdout.split("\n");
        }
        return this.enabled;
    }

    @Override
    public String[] getVirtualHostsDisabled() {
        if (this.disabled == null) {
            ArrayList result = new ArrayList();

            for (String currentAvailable : this.getVirtualHosts()) {
                for (String currentEnabled : this.getVirtualHostsEnabled()) {
                    if (currentAvailable.equals(currentEnabled)) {
                        result.add(currentEnabled);
                    }
                }
            }

            String[] tmp = new String[result.size()];
            this.disabled = (String[]) result.toArray(tmp);
        }
        return this.disabled;
    }

    @Override
    public void createVirtualHost(String domain, String[] aliases) {
        String vhost = this.template.replaceAll("%DOMAIN%", domain);

        String command1 = "echo '" + vhost + "' >> /tmp/" + domain;
        this.execute(command1);

        String command2 = "sudo mv /tmp/" + domain + " /etc/apache2/sites-available/";
        this.execute(command2);

        String command3 = "sudo chown root:root /etc/apache2/sites-available/" + domain;
        this.execute(command3);

        // @TODO setup aliases in vhost template        
        LOGGER.debug("created new virtual host for domain " + domain);
    }

    @Override
    public void deleteVirtualHost(String domain) {
        this.disableVirtualHost(domain);

        String command = "sudo rm /etc/apache2/sites-available/" + domain;
        this.execute(command);

        LOGGER.debug("disabled and deleted virtual host for domain " + domain);
    }

    @Override
    public void enableVirtualHost(String domain) {
        String command1 = "sudo a2ensite " + domain;
        this.execute(command1);

        String command2 = "sudo service apache2 reload";
        this.execute(command2);

        LOGGER.debug("enabled virtual host configuration for domain " + domain);
    }

    @Override
    public void disableVirtualHost(String domain) {
        String command1 = "sudo a2dissite " + domain;
        this.execute(command1);

        String command2 = "sudo service apache2 reload";
        this.execute(command2);

        LOGGER.debug("disabled virtual host configuration for domain " + domain);
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

    @Override
    public void upload(String domain, String type, String filename) {
        String command1 = "grep -i DocumentRoot /etc/apache2/sites-available/" + domain;
        String stdout1 = this.execute(command1);
        
        if (!stdout1.equals("")) {
            String[] tmp = stdout1.split("\n");
            String result = null;
            for (String current : tmp) {
                if (!current.toLowerCase().equals("documentroot")) {
                    result = current;
                    break;
                }
            }
            
            if (result != null) {                
                StringBuilder command2 = new StringBuilder("sudo mkdir ");
                command2.append(result);
                command2.append("/");
                switch(type) {
                    case WebserverConnectorService.UPLOAD_TYPE_CSS:
                        command2.append(WebserverConnectorService.UPLOAD_TYPE_CSS);
                        break;
                    case WebserverConnectorService.UPLOAD_TYPE_JS:
                        command2.append(WebserverConnectorService.UPLOAD_TYPE_JS);
                        break;
                    default:
                        command2.append(WebserverConnectorService.UPLOAD_TYPE_OTHER);
                        break;
                }               
                String stdout2 = this.execute(command2.toString());
                                
                File file = new File(filename);
                if (file.canRead()) {
                    String name = file.getName().replace("\\.tmp$", "");
                    // @TODO upload resource via scp ...
                }
            }
        }
    }

    @Override
    public void upload(String domain, String type, InputStream input, String name) {
        try {
            File file = File.createTempFile(name, null);
            if (file.canWrite()) {                
                byte[] buffer = new byte[1024];
                FileOutputStream output = new FileOutputStream(file);
                while(true) {
                    int read = input.read(buffer);
                    if (read > 0) {
                        output.write(buffer);
                    }
                    else {
                        break;
                    }
                }
                output.close();
            }
            this.upload(domain, type, file.getAbsolutePath());
            file.delete();
        } 
        catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }
}
