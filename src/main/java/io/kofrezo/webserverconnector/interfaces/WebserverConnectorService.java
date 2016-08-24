package io.kofrezo.webserverconnector.interfaces;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import java.io.InputStream;
import java.util.List;

/**
 * Interface to offer setup a standard way to setup several concrete webservers such as Apache and NGINX.
 *
 * @author Daniel Kröger <daniel.kroeger@kofrezo.io>
 * @author Jan Nurmse
 */
public interface WebserverConnectorService {

    String RESOURCE_TYPE_STYLESHEET = "css";
    String RESOURCE_TYPE_JAVASCRIPT = "js";
    String RESOURCE_TYPE_IMAGE = "img";
    String RESOURCE_TYPE_OTHER = "data";

    String DOMAIN_FILTER_ALL = "all";
    String DOMAIN_FILTER_ENABLED = "enabled";
    String DOMAIN_FILTER_DISABLED = "disabled";

    /**
     * Returns an array of available domains.
     *
     * @param filter filter domain mode
     * @return domains array of available domains
     */
    String[] getDomains(final String filter);

    /**
     * Creates a new domain - do nothing if already available.
     *
     * @param domain the name of the new domain
     * @param resourceImgFolder cmf_images for edit mode or cmf_images_publish for publish mode
     */
    void createDomain(final String domain, final String resourceImgFolder);

    /**
     * Deletes a domain with given name - do nothing if no available.
     *
     * @param domain the name of the domain that should be deleted
     */
    void deleteDomain(final String domain);

    /**
     * Enables a domain with given name - do nothing if already enabled.
     *
     * @param domain the name of the domain that should be enabled
     */
    void enableDomain(final String domain);

    /**
     * Disables a domain with given name - do nothing if already disabled.
     *
     * @param domain the name of the domain that should be disabled
     */
    void disableDomain(final String domain);

     /**
     * Returns an array of available resources.
     *
     * @return an array of available resources
     */
    List<String> getResources();

     /**
     * Returns an array of available resources for the given domain.
     *
     * @param domain the domain for which the resources should be detected
     * @return an array of available resources for the given domain
     */
    List<String> getResources(final String domain);

     /**
     * Returns an array of available resources for the given domain and type.
     *
     * @param domain the domain for which the resources should be detected
     * @param type resources can be filterery by type
     * @return an array of available resources for the given domain and type
     */
    List<String> getResources(final String domain, final String type);

    /**
     * This methods creates an image file representing a CmfBinaryContent on the webserver.
     * All images should be stored in a global directory.
     *
     * @param src the src of the image file as an InputStream object
     * @param resourceName the name of image file
     * @param resourceImgFolder cmf_images for edit mode or cmf_images_publish for publish mode
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     * @throws SftpException exception will be thrown if anything goes wrong while using the SFTP protocol
     */
    void createImageForCmfBinaryContent(final InputStream src, final String resourceName, final String resourceImgFolder)
            throws JSchException, SftpException;

    /**
     * Uploads a resource to the root directory of the given domain.
     *
     * @param domain the domain to which the resource should be uploaded
     * @param resourceName the name of the resource
     * @param src the src of the resource as an InputStream object
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     * @throws SftpException exception will be thrown if anything goes wrong while using the SFTP protocol
     */
    void createResource(final String domain, final String resourceName, final InputStream src)
            throws JSchException, SftpException;

    /**
     * Uploads a resource to the given uploadPath beginning from the root directory of the given domain.
     *
     * @param domain the domain to which the resource should be uploaded
     * @param uploadPath the path to which the resource should be uploaded (optional)
     * @param resourceName the name of the resource
     * @param src the src of the resource as an InputStream object
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     * @throws SftpException exception will be thrown if anything goes wrong while using the SFTP protocol
     */
    void createResource(final String domain, final String uploadPath, final String resourceName,
            final InputStream src) throws JSchException, SftpException;

    /**
     * Deletes a resource from the given domain directory.
     *
     * @param domain the domain for which the resource that should be deleted
     * @param resourceName the name of the resource that should be deleted
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     * @throws SftpException exception will be thrown if anything goes wrong while using the SFTP protocol
     */
    void deleteResource(final String domain, final String resourceName) throws JSchException, SftpException;

    /**
     * Deletes a resource from given deletePath beginning from the root directory of the given domain.
     *
     * @param domain the domain for which the resource that should be deleted
     * @param deletePath the path from where the resource should be deleted (optional)
     * @param resourceName the name of the resource that should be deleted
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     * @throws SftpException exception will be thrown if anything goes wrong while using the SFTP protocol
     */
    void deleteResource(final String domain, final String deletePath, final String resourceName)
            throws JSchException, SftpException;

    /**
     * Copies all the resources from the the source domain to the destination domain.
     *
     * @param sourceDomain the domain the resources to be copied from
     * @param destinationDomain the domain the resources to be copied to
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     */
    void copyResources(final String sourceDomain, final String destinationDomain)
            throws JSchException;

    /**
     * Copies a single resource from the the source domain to the destination domain.
     *
     * @param sourceDomain the domain the resources to be copied from
     * @param destinationDomain the domain the resources to be copied to
     * @param copyPath the path from where the resource should be copied (optional)
     * @param resourceName the name of the ressource to copy
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     */
    void copySingleResource(final String sourceDomain, final String destinationDomain, final String copyPath,
            final String resourceName) throws JSchException;

    /**
     * Copies a single resource from the the source domain to the destination domain.
     *
     * @param sourceDomain the domain the resources to be copied from
     * @param destinationDomain the domain the resources to be copied to
     * @param resourceName the name of the ressource to copy
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     */
    void copySingleResource(final String sourceDomain, final String destinationDomain,
            final String resourceName) throws JSchException;

    /**
     * Returns a byte array for a resource.
     *
     * @param path the path of the resource
     * @param resourceName the name of the ressource
     * @return a byte arrayfor the resource
     */
    byte[] readStreamForWebserverFile(final String path, final String resourceName);
}
