package io.kofrezo.webserverconnector;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import io.kofrezo.webserverconnector.apachesshconnectorservice.JSchSession;
import io.kofrezo.webserverconnector.interfaces.WebserverConnectorService;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Stack;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Apache SSH Webserver Connector Implementation
 *
 * Concrete implementation of webserver connector to setup apache webserver via
 * SSH connection.
 *
 * @author Daniel Kröger <daniel.kroeger@kofrezo.io>
 * @created Aug 8, 2015
 */
@Named
@RequestScoped
public class ApacheSSHConnectorService implements WebserverConnectorService, Serializable {

    private static final Logger LOGGER = LogManager.getLogger(ApacheSSHConnectorService.class);

    @Inject    
    private JSchSession session;
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
        if (this.session != null && this.session.getSession() != null) {
            try {
                long start = System.currentTimeMillis();

                ChannelExec channel = (ChannelExec)this.session.getSession().openChannel("exec");
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

                LOGGER.debug("command: " + command + " exit code: " + code + " message: " + stdout);
                LOGGER.debug("took " + (System.currentTimeMillis() - start) + " ms to execute");

                return stdout.toString();
            } 
            catch (JSchException | IOException | InterruptedException ex) {
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
        if (this.session != null && this.session.getSession() != null) {
            try {
                long start = System.currentTimeMillis();
                
                ChannelSftp channel = (ChannelSftp)this.session.getSession().openChannel("sftp");
                channel.put(localFilename, remoteFilename);
                channel.disconnect();
                                
                LOGGER.debug("took " + (System.currentTimeMillis() - start) + " ms to copy file");
            } 
            catch (JSchException | SftpException ex) {
                LOGGER.error(ex.getMessage(), ex);
            }
        }
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
    public String[] listVirtualHosts() {        
        String stdout = this.execute("ls /etc/apache2/sites-available");
        if (stdout.length() > 0) {
            return stdout.split("\n");
        }        
        return new String[] {};        
    }

    @Override
    public String[] listVirtualHostsEnabled() {        
        String stdout = this.execute("ls /etc/apache2/sites-enabled");
        if (stdout.length() > 0) {
            return stdout.split("\n");
        }        
        return new String[] {};        
    }

    @Override
    public String[] listVirtualHostsDisabled() {
        String[] available = this.listVirtualHosts();
        String[] enabled = this.listVirtualHostsEnabled();
        ArrayList result = new ArrayList(); 

        for (String currentAvailable : available) {
            for (String currentEnabled : enabled) {
                if (currentAvailable.equals(currentEnabled)) {
                    result.add(currentEnabled);
                }
            }
        }

        String[] tmp = new String[result.size()];
        return (String[]) result.toArray(tmp);
    }

    @Override
    public void createVirtualHost(String domain, String[] aliases) {
        String vhost = this.template.replaceAll("%DOMAIN%", domain);
        // @TODO setup aliases in vhost template

        String command1 = "echo '" + vhost + "' >> /tmp/" + domain;
        this.execute(command1);

        String command2 = "sudo mv /tmp/" + domain + " /etc/apache2/sites-available/";
        this.execute(command2);

        String command3 = "sudo chown root:root /etc/apache2/sites-available/" + domain;
        this.execute(command3);

        String command4 = "sudo mkdir -p " + this.getDocumentRoot(domain) + "/{";
        command4 += WebserverConnectorService.UPLOAD_TYPE_CSS + ",";
        command4 += WebserverConnectorService.UPLOAD_TYPE_IMG + ",";
        command4 += WebserverConnectorService.UPLOAD_TYPE_JS + ",";
        command4 += WebserverConnectorService.UPLOAD_TYPE_OTHER + "}";
        this.execute(command4);

        String command5 = "sudo chown -R www-data:www-data " + this.getDocumentRoot(domain);
        this.execute(command5);
                
        LOGGER.debug("created new virtual host for domain " + domain);
    }

    @Override
    public void deleteVirtualHost(String domain) {
        this.disableVirtualHost(domain);

        String command1 = "sudo rm -rf " + this.getDocumentRoot(domain);
        this.execute(command1);

        String command2 = "sudo rm /etc/apache2/sites-available/" + domain;
        this.execute(command2);
       
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
    public void setVirtualHostTemplate(String template) {
        this.template = template;
    }    

    @Override
    public void createResource(String domain, String type, String filename, String name) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void createResource(String domain, String type, InputStream input, String name) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void deleteResource(String domain, String type, String name) {
        throw new UnsupportedOperationException("Not supported yet.");
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
                    if (!resource.equals("")) {
                        result.push(resource);
                    }
                }
            }
            if (type == null || type.equals(WebserverConnectorService.UPLOAD_TYPE_JS)) {
                String command = "ls " + documentRoot + "/" + WebserverConnectorService.UPLOAD_TYPE_JS + "/";
                String stdout = this.execute(command);
                for (String resource : stdout.split("\n")) {
                    if (!resource.equals("")) {
                        result.push(resource);
                    }
                }
            }
            if (type == null || type.equals(WebserverConnectorService.UPLOAD_TYPE_IMG)) {
                String command = "ls " + documentRoot + "/" + WebserverConnectorService.UPLOAD_TYPE_IMG + "/";
                String stdout = this.execute(command);
                for (String resource : stdout.split("\n")) {
                    if (!resource.equals("")) {
                        result.push(resource);
                    }
                }
            }
            if (type == null || type.equals(WebserverConnectorService.UPLOAD_TYPE_OTHER)) {
                String command = "ls " + documentRoot + "/" + WebserverConnectorService.UPLOAD_TYPE_OTHER + "/";
                String stdout = this.execute(command);
                for (String resource : stdout.split("\n")) {
                    if (!resource.equals("")) {
                        result.push(resource);
                    }
                }
            }
        }

        String[] tmp = new String[result.size()];
        return result.toArray(tmp);        
    }
}
