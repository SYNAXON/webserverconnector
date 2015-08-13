package io.kofrezo.webserverconnector;

import com.jcraft.jsch.Channel;
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
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
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
                long start = System.currentTimeMillis();
                
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
               
                channel.disconnect();
                session.disconnect();
                                
                LOGGER.debug("command: " + command + " exit code: " + code + " message: " + stdout);
                LOGGER.debug("took " + (System.currentTimeMillis() - start) + " ms to execute");
                
                return stdout.toString();
            } catch (JSchException | IOException | InterruptedException ex) {
                LOGGER.error(ex.getMessage(), ex);
            }
        }

        return "";
    }

    /**
     * Copy File To Server
     *
     * Copy a file from the local given path to the remote.
     *
     * @param localFilename
     * @param remoteFilename
     */
    protected void copy(String localFilename, String remoteFilename) {
        try {
            JSch jsch = new JSch();
            jsch.addIdentity(this.privatekey, this.publickey, this.passphrase.getBytes());

            Session session = jsch.getSession(this.user, this.host, this.port);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            String command = "scp -p -t " + remoteFilename;
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();
            if (!this.isAck(in)) {
                LOGGER.warn("error while initiating to upload resource " + localFilename);
                return;
            }

            File local = new File(localFilename);
            if (local.canRead()) {
                // Step 1: Keep Timestamps
                command = "T " + (local.lastModified() / 1000) + " 0";
                command += (" " + (local.lastModified() / 1000) + " 0\n");
                out.write(command.getBytes());
                out.flush();
                if (!this.isAck(in)) {
                    LOGGER.warn("error while uploading meta information for resource " + localFilename);
                    return;
                }

                // Step 2: Setup Permissions
                long filesize = local.length();
                command = "C0644 " + filesize + " ";
                if (localFilename.lastIndexOf('/') > 0) {
                    command += localFilename.substring(localFilename.lastIndexOf('/') + 1);
                } else {
                    command += localFilename;
                }
                command += "\n";
                out.write(command.getBytes());
                out.flush();

                if (!this.isAck(in)) {
                    LOGGER.warn("error while getting acknowledgement for resource " + localFilename);
                    return;
                }

                // Step 3: Upload File
                FileInputStream fis = new FileInputStream(localFilename);
                byte[] buffer = new byte[1024];
                while (true) {
                    int read = fis.read(buffer, 0, buffer.length);
                    if (read <= 0) {
                        break;
                    }
                    out.write(buffer, 0, read); //out.flush();
                }
                fis.close();
                
                // Step 4: Finish Uploading
                buffer[0] = 0;
                out.write(buffer, 0, 1);
                out.flush();
                
                if (!this.isAck(in)) {
                    LOGGER.warn("error while uploading resource " + localFilename);                    
                }
                out.close();

                channel.disconnect();
                session.disconnect();
            }
        } 
        catch (JSchException | IOException ex) {
            LOGGER.warn(ex.getMessage(), ex);
        }
    }

    /**
     * Check Input Stream For Acknowledgement
     *
     * Check the given input stream for acknowledgment or if error.
     *
     * @param in     
     *
     * @return acknowledged
     */
    protected boolean isAck(InputStream in) {
        try {
            int result = in.read();
            if (result <= 0) {
                return true;
            } else {
                return false;
            }
        } catch (IOException ex) {
            LOGGER.warn(ex.getMessage(), ex);
        }
        return false;
    }
    
    /**
     * Return Document Root For Domain
     * 
     * Return the document root defined in the virtual host configuration or
     * null if not available
     * 
     * @param domain
     * @return document root | null
     */
    protected String getDocumentRoot(String domain) {
        String command1 = "grep -i DocumentRoot /etc/apache2/sites-available/" + domain;
        String stdout = this.execute(command1);

        if (!stdout.equals("")) {
            return stdout.replace("DocumentRoot", "").trim();
        }
        
        return null;
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
    public String[] listVirtualHosts() {
        if (this.available == null) {
            String stdout = this.execute("ls /etc/apache2/sites-available");
            this.available = stdout.split("\n");
        }
        return this.available;
    }

    @Override
    public String[] listVirtualHostsEnabled() {
        if (this.enabled == null) {
            String stdout = this.execute("ls /etc/apache2/sites-enabled");
            this.enabled = stdout.split("\n");
        }
        return this.enabled;
    }

    @Override
    public String[] listVirtualHostsDisabled() {
        if (this.disabled == null) {
            ArrayList result = new ArrayList();

            for (String currentAvailable : this.listVirtualHosts()) {
                for (String currentEnabled : this.listVirtualHostsEnabled()) {
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

        StringBuilder command = new StringBuilder();
        command.append("echo '").append(vhost).append("' >> /tmp/").append(domain).append(" && ");
        command.append("sudo mv /tmp/").append(domain).append(" /etc/apache2/sites-available/ &&");
        command.append("sudo chown root:root /etc/apache2/sites-available/").append(domain).append(" &&");        
        String documentRoot = this.getDocumentRoot(domain);
        command.append("sudo mkdir -p ").append(documentRoot).append("/" + WebserverConnectorService.UPLOAD_TYPE_CSS + " &&");
        command.append("sudo mkdir -p " + documentRoot + "/" + WebserverConnectorService.UPLOAD_TYPE_IMG + " &&");
        command.append("sudo mkdir -p ").append(documentRoot).append("/" + WebserverConnectorService.UPLOAD_TYPE_JS + " &&");       
        command.append("sudo mkdir -p ").append(documentRoot).append("/" + WebserverConnectorService.UPLOAD_TYPE_OTHER + " &&");
        command.append("sudo chown -R www-data:www-data " + documentRoot);       
                
        // @TODO setup aliases in vhost template
        
        this.execute(command.toString());
        
        LOGGER.debug("created new virtual host for domain " + domain);
    }

    @Override
    public void deleteVirtualHost(String domain) {
        this.disableVirtualHost(domain);

        String command = "sudo rm -rf " + this.getDocumentRoot(domain);
        command += "sudo rm /etc/apache2/sites-available/" + domain;
        this.execute(command);                
        
        LOGGER.debug("disabled and deleted virtual host for domain " + domain);
    }

    @Override
    public void enableVirtualHost(String domain) {
        String command = "sudo a2ensite " + domain + " && sudo service apache2 reload";
        this.execute(command);

        LOGGER.debug("enabled virtual host configuration for domain " + domain);
    }

    @Override
    public void disableVirtualHost(String domain) {
        String command = "sudo a2dissite " + domain + " && sudo service apache2 reload";
        this.execute(command);

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
    public void createResource(String domain, String type, String filename, String name) {
        String documentRoot = this.getDocumentRoot(domain);
        if (documentRoot != null) {            
            File file = new File(filename);
            if (file.canRead()) {
                switch (type) {
                    case WebserverConnectorService.UPLOAD_TYPE_CSS:
                    this.copy(filename, documentRoot + "/" + WebserverConnectorService.UPLOAD_TYPE_CSS + "/" + name);
                    break;
                case WebserverConnectorService.UPLOAD_TYPE_JS:
                    this.copy(filename, documentRoot + "/" + WebserverConnectorService.UPLOAD_TYPE_JS + "/" + name);
                    break;
                case WebserverConnectorService.UPLOAD_TYPE_IMG:
                    this.copy(filename, documentRoot + "/" + WebserverConnectorService.UPLOAD_TYPE_IMG + "/" + name);
                    break;                
                default:
                    this.copy(filename, documentRoot + "/" + WebserverConnectorService.UPLOAD_TYPE_OTHER + "/" + name);
                    break;
                }                
            }
        }        
    }

    @Override
    public void createResource(String domain, String type, InputStream input, String name) {
        try {
            File file = File.createTempFile(name, null);
            if (file.canWrite()) {
                byte[] buffer = new byte[1024];
                FileOutputStream output = new FileOutputStream(file);
                while (true) {
                    int read = input.read(buffer);
                    if (read > 0) {
                        output.write(buffer);
                    } else {
                        break;
                    }
                }
                output.close();
            }
            LOGGER.debug("created temp file starting file upload via ssh now!");
            this.createResource(domain, type, file.getAbsolutePath(), name);
            file.delete();
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void deleteResource(String domain, String type, String name) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String[] listResources(String domain, String type) {
        Stack<String> result = new Stack();
        String documentRoot = this.getDocumentRoot(domain);
        
        if (documentRoot != null) {
            if (type == null || type.equals(WebserverConnectorService.UPLOAD_TYPE_CSS)) {
                String command = "ls " + documentRoot + "/" + WebserverConnectorService.UPLOAD_TYPE_CSS + "/";
                String stdout = this.execute(command);
                for (String resource : stdout.split("\n")) {
                    if (!resource.equals(""))
                        result.push(resource);
                }
            }
            if (type == null || type.equals(WebserverConnectorService.UPLOAD_TYPE_JS)) {
                String command = "ls " + documentRoot + "/" + WebserverConnectorService.UPLOAD_TYPE_JS + "/";
                String stdout = this.execute(command);
                for (String resource : stdout.split("\n")) {
                    if (!resource.equals(""))
                        result.push(resource);
                }
            }
            if (type == null || type.equals(WebserverConnectorService.UPLOAD_TYPE_IMG)) {
                String command = "ls " + documentRoot + "/" + WebserverConnectorService.UPLOAD_TYPE_IMG + "/";
                String stdout = this.execute(command);
                for (String resource : stdout.split("\n")) {
                    if (!resource.equals(""))
                        result.push(resource);
                }
            }
            if (type == null || type.equals(WebserverConnectorService.UPLOAD_TYPE_OTHER)) {
                String command = "ls " + documentRoot + "/" + WebserverConnectorService.UPLOAD_TYPE_OTHER + "/";
                String stdout = this.execute(command);
                for (String resource : stdout.split("\n")) {
                    if (!resource.equals(""))
                        result.push(resource);
                }
            }
        }                
        
        String[] tmp = new String[result.size()];
        return result.toArray(tmp);
    }
}
