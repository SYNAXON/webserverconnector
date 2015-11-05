package io.kofrezo.webserverconnector.interfaces;

import java.io.InputStream;

/**
 * Webserver Connector Service Interface
 *
 * Interface to offer setup a standard way to setup several concrete webservers such as Apache and NGINX.
 *
 * @author Daniel Kröger <daniel.kroeger@kofrezo.io>
 * @created Aug 8, 2015
 */
public interface WebserverConnectorService {

    public static final String RESOURCE_TYPE_STYLESHEET = "css";
    public static final String RESOURCE_TYPE_JAVASCRIPT = "js";
    public static final String RESOURCE_TYPE_IMAGE = "img";
    public static final String RESOURCE_TYPE_OTHER = "data";
    
    public static final String DOMAIN_FILTER_ALL = "all";
    public static final String DOMAIN_FILTER_ENABLED = "enabled";
    public static final String DOMAIN_FILTER_DISABLED = "disabled";
    
    public static final String RESOURCE_IMG_FOLDER = "/var/www/cmf_images";

    /**
     * Get Domains
     *
     * Return array of available domains
     *
     * @param filter
     * @return domains
     */
    public String[] getDomains(String filter);    

    /**
     * Create New Domain
     * 
     * Create new domain - do nothing if already available          
     *
     * @param domain
     * @param aliases
     */
    public void createDomain(String domain, String[] aliases);

    /**
     * Delete Domain
     * 
     * Delete domain with given name - do nothing if no available
     *
     * @param domain
     */
    public void deleteDomain(String domain);

    /**
     * Enable Domain
     * 
     * Enable domain with given name - do nothing if already enabled
     *
     * @param domain
     */
    public void enableDomain(String domain);

    /**
     * Disable Domain
     * 
     * Disable domain with given name - do nothing if already disabled
     *
     * @param domain
     */
    public void disableDomain(String domain);    
    
     /**
     * Get Resources
     * 
     * Return array of available resource for domain and type if type is null
     * return all types if domain is null return all domains.
     * 
     * @param domain
     * @param type 
     * @return resource list
     */
    public String[] getResources(String domain, String type);
    
    /**
     * Upload Resource
     * 
     * @param domain
     * @param type
     * @param src 
     * @param dstName
     */
    public void createResource(String domain, String type, String src, String dstName);
    
    /**
     * This methods creates an image file on the webserver. All images should be stored in a global directory.
     * 
     * @param src
     * @param dstName 
     */
    public void createImage(InputStream src, String dstName);    
    
    /**
     * Upload Resource
     *      
     * @param domain
     * @param type
     * @param src
     * @param dstName
     */
    public void createResource(String domain, String type, InputStream src, String dstName);
    
    /**
     * Delete Resource          
     * 
     * @param domain
     * @param type
     * @param name 
     */
    public void deleteResource(String domain, String type, String name);       
}
