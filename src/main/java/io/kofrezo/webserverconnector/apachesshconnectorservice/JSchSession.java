package io.kofrezo.webserverconnector.apachesshconnectorservice;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javax.annotation.PreDestroy;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * JSch Session Bean
 * 
 * This is a utility class to open and close ssh sessions to a webserver
 * 
 * @author Daniel Kröger <daniel.kroeger@kofrezo.io>
 * @version 19.08.2015
 */
@Named
@RequestScoped
public class JSchSession
{        
    private static final Logger LOGGER = LogManager.getLogger(JSchSession.class);
    
    private Properties properties;
    private Session session;
    
    public JSchSession() {}
        
    protected String getConfig(String key) {
        if (this.properties == null) {
            this.properties = new Properties();
            InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration.properties");
            if (in == null) {
                LOGGER.error("cannot load configuration properties");                
            } 
            else {                
                try {
                    this.properties.load(in);                    
                }
                catch(IOException ex) {
                    LOGGER.error(ex.getMessage(), ex);
                }
            }
        }
        return this.properties.getProperty(key);        
    }
        
    public Session getSession() {
        if (this.session == null) {
            try {
                JSch jsch = new JSch();
                jsch.addIdentity(
                    this.getConfig("webserverconnector.apachessh.privatekey"), 
                    this.getConfig("webserverconnector.apachessh.publickey"), 
                    this.getConfig("webserverconnector.apachessh.privatekey").getBytes()
                );
                this.session = jsch.getSession(
                    this.getConfig("webserverconnector.apachessh.user"), 
                    this.getConfig("webserverconnector.apachessh.host"),
                    Integer.valueOf(this.getConfig("webserverconnector.apachessh.port"))
                );
                this.session.setConfig("StrictHostKeyChecking", "no");
                this.session.connect();            
                LOGGER.debug("started ssh session");                
            }
            catch (JSchException ex) {
                LOGGER.error(ex.getMessage(), ex);
            }
        }        
        return this.session;
    }
     
    @PreDestroy
    public void preDestroy() {
        if (this.session != null && this.session.isConnected()) {
            this.session.disconnect();
            LOGGER.debug("closed ssh session");
        }
    }   
}
