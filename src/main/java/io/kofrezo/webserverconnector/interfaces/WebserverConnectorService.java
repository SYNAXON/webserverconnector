package io.kofrezo.webserverconnector.interfaces;

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

    /**
     * Check For Valid Setup
     *
     * Check if the current connector object is setup correct to execute the standard commands.
     *
     * @return
     */
    public boolean isValid();

    /**
     * Get Virtual Hosts Available
     *
     * Return a list of all available virtual hosts
     *
     * @return virtual hosts available
     */
    public String[] getVirtualHosts();

    /**
     * Get Virtual Hosts Enabled
     *
     * Return a list of all enabled virtual hosts
     *
     * @return virtual hosts enabled
     */
    public String[] getVirtualHostsEnabled();

    /**
     * Get Virtual Hosts Disabled
     *
     * Return a list of all disabled virtual hosts
     *
     * @return virtual hosts disabled
     */
    public String[] getVirtualHostsDisabled();

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
     * Set Authentication Credentials
     *
     * Set additional credentials for authentication a concrete implementation my need.
     *
     * @param credentials
     */
    public void setAuthenticationCredentials(HashMap<String, String> credentials);
}
