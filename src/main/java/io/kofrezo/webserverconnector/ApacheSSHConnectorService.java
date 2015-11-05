package io.kofrezo.webserverconnector;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import io.kofrezo.webserverconnector.interfaces.WebserverConnectorService;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Stack;
import java.util.Vector;
import javax.annotation.PreDestroy;
import javax.enterprise.context.RequestScoped;
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

    private final String ENVIRMONMENT = System.getProperty("cmf.environment", "development");
    private Properties properties;
    private Session session;    

    @PreDestroy
    public void deinit() {
        if (this.getSession() != null && this.getSession().isConnected()) {
            this.getSession().disconnect();
            LOGGER.debug("closing ssh connection to host");
        }
    }
    
    private Properties getProperties()
    {
        if (this.properties == null) {
            try {
                this.properties = new Properties();
                String propertiesPath = ENVIRMONMENT + "/webserverconnector.properties";
                InputStream is = this.getClass().getClassLoader().getResourceAsStream(propertiesPath);
                this.properties.load(is);
            } 
            catch (IOException ex) {
                LOGGER.error("/(WEB-INF|META-INF)/classes/webserverconnector.properties does not exist or is not properly configured");
            }
        }
        return this.properties;
    }

    private Session getSession()
    {        
        if (this.session == null || !this.session.isConnected()) {
            try {
                JSch jsch = new JSch();
                jsch.addIdentity(
                        this.getProperties().getProperty("connector.apachessh.privatekey"),
                        this.getProperties().getProperty("connector.apachessh.publickey"),
                        this.getProperties().getProperty("connector.apachessh.passphrase").getBytes()
                );
                this.session = jsch.getSession(
                        this.getProperties().getProperty("connector.apachessh.user"),
                        this.getProperties().getProperty("connector.apachessh.host"),
                        Integer.parseInt(this.getProperties().getProperty("connector.apachessh.port"))        
                );
                this.session.setConfig(
                        "StrictHostKeyChecking",
                        this.getProperties().getProperty("connector.apachessh.stricthostkeychecking", "yes")
                );
                this.session.connect();
                LOGGER.debug("opening ssh session to host");
            }
            catch (JSchException ex) {
                LOGGER.error("establishing ssh connection to host failed", ex);
            }        
        }
        return this.session;
    }    
    
    private String execute(String command)
    {
        long start = System.currentTimeMillis();       
                 
        try {
            ChannelExec channel = (ChannelExec)this.getSession().openChannel("exec");            
            channel.setCommand(command);
            channel.setErrStream(System.err);
            InputStream is = channel.getInputStream();
            channel.connect();
            
            byte[] data = new byte[1024];
            StringBuilder stdout = new StringBuilder();
            while (true) {
                while (is.available() > 0) {
                    int read = is.read(data, 0, 1024);
                    if (read < 0) {
                        break;
                    }
                    stdout.append(new String(data, 0, read));
                }
                if (channel.isClosed()) {
                    try {
                        if (is.available() > 0) {
                            continue;
                        }
                        break;
                    } 
                    catch (IOException ex) {
                        LOGGER.error("executing command via ssh failed", ex);
                    }
                }
                Thread.sleep(500); // I absolutely don't know if this is a good value
            }            
            
            is.close();
            channel.disconnect();            
                        
            LOGGER.debug("took " + (System.currentTimeMillis() - start) + " ms to execute command via ssh: " + command);
            
            return stdout.toString();
        } 
        catch (IOException | InterruptedException | JSchException ex) {
            LOGGER.error("executing command via ssh failed", ex);
        } 
        
        return "";
    }
    
    private String getTemplate() {
        StringBuilder result = new StringBuilder();
        
        try {
            BufferedInputStream bis = null;
            String template = this.getProperties().getProperty("connector.apachessh.template");
            if (template != null) {
                String templatePath = ENVIRMONMENT + "/" + template;
                bis = new BufferedInputStream(this.getClass().getClassLoader().getResourceAsStream(templatePath));
            }

            if (bis != null) {
                byte[] buffer = new byte[1024];
                while(true) {
                    int read = bis.read(buffer);
                    if (read > -1) {
                        result.append(new String(buffer, 0, read));
                    }
                    else {
                        break;
                    }
                }
            }
        }
        catch(FileNotFoundException ex) {
            LOGGER.error("loading template for new domain failed", ex);
        } 
        catch (IOException ex) {
            LOGGER.error("loading template for new domain failed", ex);
        }
        
        return result.toString();
    }
    
    private String getDocumentRoot(String template) {
        String docroot = null;
        
        try {
            int docrootBegin = template.indexOf("DocumentRoot") + 12;            
            int docrootEnd = template.indexOf("\n", docrootBegin);            

            docroot = template.substring(docrootBegin, docrootEnd).trim();
            LOGGER.debug("document root is: " + docroot);
        }
        catch(StringIndexOutOfBoundsException ex) {
            LOGGER.warn("failed to get document root from template");
        }
        
        return docroot;
    }
    
    private String getFilenameForLs(String ls) {
        String[] fields = ls.split(" ");
        return fields[fields.length - 1].trim();
    }
    
    @Override
    public String[] getDomains(String filter) {
        String command;        
        StringBuilder stdout = new StringBuilder();
        
        switch(filter) {
            case WebserverConnectorService.DOMAIN_FILTER_ENABLED:
                command = "grep -P '^\\s+ServerName' " + this.getProperties().getProperty("connector.apachessh.sitesenabled", "/etc/apache2/sites-enabled/") + "*";
                stdout.append(this.execute(command));
                break;
            case WebserverConnectorService.DOMAIN_FILTER_DISABLED:
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.                
            default:
                command = "grep -P '^\\s+ServerName' " + this.getProperties().getProperty("connector.apachessh.sitesavailable", "/etc/apache2/sites-available/") + "*";
                stdout.append(this.execute(command));
                break;
        }
        
        ArrayList domains = new ArrayList();
        for (String line : stdout.toString().split("\n")) {
            if (!line.trim().equals("")) {
                String[] tmp = line.split("ServerName");
                if (tmp.length > 1) {
                    String domain = tmp[1].trim();
                    domains.add(domain);
                    LOGGER.debug("found domain " + domain);
                }
            }            
        }
        LOGGER.debug("found " + domains.size() + " domains");
        
        String[] tmp = new String[domains.size()];
        return (String[])domains.toArray(tmp);
    }

    @Override
    public void createDomain(String domain, String[] aliases) {
        String template = this.getTemplate().replace("%DOMAIN%", domain);
        
        // @TODO setup aliases for domain ...                
        
        Stack<String> commands = new Stack();
        
        String filename = this.getProperties().getProperty("connector.apachessh.sitesavailable", "/etc/apache2/sites-available/") + domain + ".conf";
        String command1 = "echo '" + template + "' >> " + filename;
        commands.push(command1);
        
        String docroot = this.getDocumentRoot(template);
        docroot += (docroot.endsWith("/") ? "" : "/");       
        if (!docroot.equals("")) {
            String command4 = "mkdir -p " + docroot + "{";                        
            command4 += WebserverConnectorService.RESOURCE_TYPE_IMAGE + ",";
            command4 += WebserverConnectorService.RESOURCE_TYPE_JAVASCRIPT + ",";
            command4 += WebserverConnectorService.RESOURCE_TYPE_OTHER + ",";
            command4 += WebserverConnectorService.RESOURCE_TYPE_STYLESHEET;
            command4 += "}";
            commands.push(command4);                      
        }
         
        while(!commands.empty()) {            
            this.execute(commands.firstElement());
            commands.remove(0);
        }
    }

    @Override
    public void deleteDomain(String domain) {
        String filename = this.getProperties().getProperty("connector.apachessh.sitesavailable", "/etc/apache2/sites-available/") + domain + ".conf";
        String command = "sudo a2dissite " + domain + ".conf; sudo /etc/init.d/apache2 reload; rm " + filename;
        this.execute(command);
    }

    @Override
    public void enableDomain(String domain) {        
        String command = "sudo a2ensite " + domain + ".conf && sudo /etc/init.d/apache2 reload";
        this.execute(command);
    }

    @Override
    public void disableDomain(String domain) {
        String command = "sudo a2dissite " + domain + ".conf && sudo /etc/init.d/apache2 reload";
        this.execute(command);
    }

    @Override
    public String[] getResources(String domain, String type) {        
        try {
            String[] types = null;
            String[] domains = this.getDomains(WebserverConnectorService.DOMAIN_FILTER_ALL);
            
            if (domain != null) {
                domains = new String[] { domain };
            }
            
            if (type != null) {
                types = new String[] { type };
            }
            else {
                types = new String[] {
                    WebserverConnectorService.RESOURCE_TYPE_IMAGE,
                    WebserverConnectorService.RESOURCE_TYPE_JAVASCRIPT,
                    WebserverConnectorService.RESOURCE_TYPE_OTHER,
                    WebserverConnectorService.RESOURCE_TYPE_STYLESHEET
                };
            }
            
            Stack<String> resources = new Stack();
            ChannelSftp channel = (ChannelSftp)this.getSession().openChannel("sftp");
            channel.connect();
      
            String rootPath = "/var/www";
            Vector rootPathResult = channel.ls(rootPath);
            Stack<String> domainPaths = new Stack();
            for (Object file : rootPathResult) {
                String filename = this.getFilenameForLs(file.toString());
                domainPaths.push(filename);
            }

            for (String curDomain : domains) {
                boolean exists = false;
                for (String domainPath : domainPaths) {
                    if (domainPath.equals(curDomain)) {
                        exists = true;
                        break;
                    }
                }
                
                if (exists == true) {
                    for (String curType : types) {
                        String path = "/var/www/" + curDomain + "/" + curType;
                        LOGGER.debug("listing resources for " + path);

                        Vector files = channel.ls(path);                    
                        for (Object file : files) {
                            String filename = this.getFilenameForLs(file.toString());
                            if (!filename.equals(".") && !filename.equals("..")) {
                                resources.push(path + "/" + filename);
                            }
                        }
                    }
                }
            }
            
            channel.disconnect();
            
            String[] tmp = new String[resources.size()];
            return (String[])resources.toArray(tmp);
        } 
        catch (JSchException | SftpException ex) {
            LOGGER.error("error while listing available resources", ex);
        }
        
        return new String[] {};
    }

    @Override
    public void createResource(String domain, String type, String src, String dstName) {
        try {
            ChannelSftp channel = (ChannelSftp)this.getSession().openChannel("sftp");
            channel.connect();
            
            String path = "/var/www/" + domain + "/" + type + "/" + dstName;
            channel.put(src, path);
            channel.disconnect();
        } 
        catch (JSchException | SftpException ex) {
            LOGGER.debug("can not create resource", ex);
        }
    }

    @Override
    public void createResource(String domain, String type, InputStream src, String dstName) {
        try {
            ChannelSftp channel = (ChannelSftp)this.getSession().openChannel("sftp");
            channel.connect();
            
            String path = "/var/www/" + domain + "/" + type + "/" + dstName;
            channel.put(src, path);
            channel.disconnect();
        } 
        catch (JSchException | SftpException ex) {
            LOGGER.debug("can not create resource", ex);
        }
    }

    @Override
    public void deleteResource(String domain, String type, String name) {
        try {
            ChannelSftp channel = (ChannelSftp)this.getSession().openChannel("sftp");
            channel.connect();
            
            String path = "/var/www/" + domain + "/" + type + "/" + name;
            channel.rm(path);
            channel.disconnect();
        } 
        catch (JSchException | SftpException ex) {
            LOGGER.debug("can not delete resource", ex);
        }
    }        
}
