package io.kofrezo.webserverconnector.interfaces;

import java.io.InputStream;
import java.util.HashMap;

/**
 * Webserver Connector Service Interface
 *
 * Interface to offer setup a standard way to setup several concrete webservers such as Apache and NGINX.
 *
 * @author Daniel Kröger <daniel.kroeger@kofrezo.io>
 * @created Aug 8, 2015
 */
public interface WebserverConnectorService {

    public static final String UPLOAD_TYPE_CSS = "css";
    public static final String UPLOAD_TYPE_JS = "js";
    public static final String UPLOAD_TYPE_IMG = "img";
    public static final String UPLOAD_TYPE_OTHER = "data";        

    /**
     * Get Virtual Hosts Available
     *
     * Return a list of all available virtual hosts
     *
     * @return virtual hosts available
     */
    public String[] listVirtualHosts();

    /**
     * Get Virtual Hosts Enabled
     *
     * Return a list of all enabled virtual hosts
     *
     * @return virtual hosts enabled
     */
    public String[] listVirtualHostsEnabled();

    /**
     * Get Virtual Hosts Disabled
     *
     * Return a list of all disabled virtual hosts
     *
     * @return virtual hosts disabled
     */
    public String[] listVirtualHostsDisabled();

    /**
     * Create New Virtual Host
     *
     * Creates a new virtual host based on the template file set.
     *
     * All occurrences of the String %DOMAIN% in template file are replaced with the domain name given. Should do
     * nothing if the virtual host already exists. Should not enable the new virtual host.
     *
     * @param domain
     * @param aliases
     */
    public void createVirtualHost(String domain, String[] aliases);

    /**
     * Delete Virtual Host
     *
     * Deletes an existing virtual host.
     *
     * Deletes the existing virtual host for the given domain. Should do nothing if the virtual host does not exist.
     *
     * @param domain
     */
    public void deleteVirtualHost(String domain);

    /**
     * Enable Virtual Host
     *
     * Enables an existing virtual host.
     *
     * Enables an existing virtual host matching the given domain name. Should do nothing if the virtual host does not
     * exist.
     *
     * @param domain
     */
    public void enableVirtualHost(String domain);

    /**
     * Disable Virtual Host
     *
     * Disables an existing virtual host.
     *
     * Disables an existing virtual host matching the given domain name. Should do nothing if the virtual host does not
     * exist.
     *
     * @param domain
     */
    public void disableVirtualHost(String domain);

    /**
     * Set Template Filename
     *
     * Set the filename to the template used for new virtual hosts.
     *
     * @param filename
     */
    public void setVirtualHostTemplate(String filename);    
    
    /**
     * Upload Resource To Webserver
     * 
     * Upload the given resource to webserver with the same filename.
     * 
     * @param domain
     * @param type
     * @param filename 
     * @param name
     */
    public void createResource(String domain, String type, String filename, String name);
    
    /**
     * Upload Resource To Webserver
     * 
     * Upload the given resource to webserver from the given inputstream and
     * use the given name.
     * 
     * Overwrite resource if it already exists and has changed.
     * 
     * @param domain
     * @param type
     * @param input
     * @param name
     */
    public void createResource(String domain, String type, InputStream input, String name);
    
    /**
     * Delete Resource From Webserver
     * 
     * Delete the given resource identified by name and type at document root
     * from the webserver.
     * 
     * Do nothing if the resource does not exist.
     * 
     * @param domain
     * @param type
     * @param name 
     */
    public void deleteResource(String domain, String type, String name);
    
    /**
     * Get Available Resources For Type
     * 
     * List all available resources for the given doman and type.
     * 
     * Type may be null or a resource constant. If type is null all resources
     * are returned.
     * 
     * @param domain
     * @param type
     * 
     * @return resource list
     */
    public String[] listResources(String domain, String type);
}
