package io.kofrezo.webserverconnector;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import io.kofrezo.webserverconnector.interfaces.WebserverConnectorService;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import javax.annotation.PreDestroy;
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
 * @author Jan Nurmse
 */
@Named
@RequestScoped
public class ApacheSSHConnectorService implements WebserverConnectorService, Serializable {

    private static final Logger LOGGER = LogManager.getLogger(ApacheSSHConnectorService.class);
    private static final long serialVersionUID = -1150221495148210253L;

    private static final String ROOT_DIR = "/var/www/";

    private final int bufferSize = 1024;
    private final String ENVIRMONMENT = System.getProperty("cmf.environment", "development");
    private Properties properties;
    private Session session;

    /**
     * Closing ssh connection.
     */
    @PreDestroy
    public void deinit() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            LOGGER.debug("closing ssh connection to host");
        }
    }

    private Properties getProperties() {
        if (properties == null) {
            try {
                properties = new Properties();
                String propertiesPath = ENVIRMONMENT + "/webserverconnector.properties";
                InputStream is = getClass().getClassLoader().getResourceAsStream(propertiesPath);
                properties.load(is);
            } catch (IOException ex) {
                String error = "/(WEB-INF|META-INF)/classes/webserverconnector.properties does "
                        + "not exist or is not properly configured";
                LOGGER.error(error, ex);
            }
        }
        return properties;
    }

    /**
     * This method provides the ability to override the whole connector properties.
     *
     * @param properties the properties to be set
     */
    public void setProperties(final Properties properties) {
        this.properties = properties;
    }

    private Session getSession() throws JSchException {
        if (session == null || !session.isConnected()) {
            JSch jsch = new JSch();
            jsch.addIdentity(
                    getProperties().getProperty("connector.apachessh.privatekey"),
                    getProperties().getProperty("connector.apachessh.publickey"),
                    getProperties().getProperty("connector.apachessh.passphrase")
                            .getBytes(StandardCharsets.UTF_8)
            );
            session = jsch.getSession(
                   getProperties().getProperty("connector.apachessh.user"),
                   getProperties().getProperty("connector.apachessh.host"),
                    Integer.parseInt(getProperties().getProperty("connector.apachessh.port"))
            );
            session.setConfig(
                    "StrictHostKeyChecking",
                    getProperties().getProperty("connector.apachessh.stricthostkeychecking", "yes")
            );
            session.connect();
            LOGGER.debug("opening ssh session to host");
        }
        return session;
    }

    private String execute(final String command) {
        long start = System.currentTimeMillis();

        try {
            ChannelExec channel = (ChannelExec) getSession().openChannel("exec");
            channel.setCommand(command);
            channel.setErrStream(System.err);
            StringBuilder stdout;
            try (InputStream is = channel.getInputStream()) {
                channel.connect();
                stdout = new StringBuilder();
                readStdout(channel, is, stdout);
            }
            channel.disconnect();

            LOGGER.debug("took " + (System.currentTimeMillis() - start)
                    + " ms to execute command via ssh: " + command);

            return stdout.toString();
        } catch (IOException | InterruptedException | JSchException ex) {
            LOGGER.error("executing command via ssh failed", ex);
        }
        return "";
    }

    private void readStdout(final ChannelExec channel, final InputStream is, final StringBuilder stdout)
            throws IOException, InterruptedException {
        byte[] data = new byte[bufferSize];
        while (true) {
            while (is.available() > 0) {
                int read = is.read(data, 0, bufferSize);
                if (read < 0) {
                    break;
                }
                stdout.append(new String(data, 0, read, StandardCharsets.UTF_8));
            }
            if (channel.isClosed()) {
                try {
                    if (is.available() > 0) {
                        continue;
                    }
                    break;
                } catch (IOException ex) {
                    LOGGER.error("executing command via ssh failed", ex);
                }
            }
            Thread.sleep(500); // I absolutely don't know if this is a good value
        }
    }

    private String getTemplate() {
        StringBuilder result = new StringBuilder();
        BufferedInputStream bis = null;
        try {
            String template = getProperties().getProperty("connector.apachessh.template");
            if (template != null) {
                bis = new BufferedInputStream(
                        getClass().getClassLoader().getResourceAsStream(ENVIRMONMENT + "/" + template));
            }
            readResult(bis, result);
        } catch (IOException ex) {
            LOGGER.error("loading template for new domain failed", ex);
        }
        return result.toString();
    }

    private void readResult(final BufferedInputStream bis, final StringBuilder result) throws IOException {
        if (bis != null) {
            byte[] buffer = new byte[bufferSize];
            while (true) {
                int read = bis.read(buffer);
                if (read > -1) {
                    result.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                } else {
                    break;
                }
            }
            try {
                bis.close();
            } catch (IOException ex) {
                LOGGER.error("loading template for new domain failed", ex);
            }
        }
    }

    private String getDocumentRoot(final String template) {
        String docroot = null;

        try {
            int docrootBegin = template.indexOf("DocumentRoot") + 12;
            int docrootEnd = template.indexOf('\n', docrootBegin);

            docroot = template.substring(docrootBegin, docrootEnd).trim();
            LOGGER.debug("document root is: " + docroot);
        } catch (StringIndexOutOfBoundsException ex) {
            LOGGER.warn("failed to get document root from template");
        }

        return docroot;
    }

    private String getFilenameForLs(final String ls) {
        String[] fields = ls.split(" ");
        return fields[fields.length - 1].trim();
    }

    @Override
    public String[] getDomains(final String filter) {
        String command;
        StringBuilder stdout = new StringBuilder();

        switch (filter) {
            case WebserverConnectorService.DOMAIN_FILTER_ENABLED:
                String sitesenabled = "connector.apachessh.sitesenabled";
                command = "grep -P '^\\s+ServerName' "
                        + getProperties().getProperty(sitesenabled, "/etc/apache2/sites-enabled/") + "*";
                stdout.append(execute(command));
                break;
            case WebserverConnectorService.DOMAIN_FILTER_DISABLED:
                throw new UnsupportedOperationException("Not supported yet.");
            default:
                String sitesavailable = "connector.apachessh.sitesavailable";
                command = "grep -P '^\\s+ServerName' "
                        + getProperties().getProperty(sitesavailable, "/etc/apache2/sites-available/") + "*";
                stdout.append(execute(command));
                break;
        }
        List<String> domains = readDomainsFromStdout(stdout);
        String[] tmp = new String[domains.size()];
        return (String[]) domains.toArray(tmp);
    }

    private List<String> readDomainsFromStdout(final StringBuilder stdout) {
        List<String> domains = new ArrayList<>();
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
        return domains;
    }

    @Override
    public void createDomain(final String domain) {
        if (domain != null) {
            String template = getTemplate().replace("%DOMAIN%", domain);
            Stack<String> commands = new Stack();
            String filename = getProperties().getProperty("connector.apachessh.sitesavailable",
                    "/etc/apache2/sites-available/") + domain + ".conf";
            String command1 = "echo '" + template + "' >> " + filename;
            commands.push(command1);
            addAdditionalCommands(commands, template);
            while (!commands.empty()) {
                execute(commands.firstElement());
                commands.remove(0);
            }
        }
    }

    private void addAdditionalCommands(final Stack<String> commands, final String template) {
        String docroot = getDocumentRoot(template);
        if (!docroot.endsWith("/")) {
            docroot += "/";
        }
        if (!docroot.equals("")) {
            String command4 = "mkdir -p " + docroot + "{";
            command4 += WebserverConnectorService.RESOURCE_TYPE_JAVASCRIPT + ",";
            command4 += WebserverConnectorService.RESOURCE_TYPE_OTHER + ",";
            command4 += WebserverConnectorService.RESOURCE_TYPE_STYLESHEET;
            command4 += "}";
            commands.push(command4);
            // all images will be stored in a global directory and not in a domain specific one
            // so that it is necessary to create the global directory and a symlink
            String command5 = "mkdir -p " + RESOURCE_IMG_FOLDER;
            commands.push(command5);
            String command6 = "ln -s " + RESOURCE_IMG_FOLDER + " "
                    + docroot + WebserverConnectorService.RESOURCE_TYPE_IMAGE;
            commands.push(command6);
        }
    }

    @Override
    public void deleteDomain(final String domain) {
        String sitesavailable = "connector.apachessh.sitesavailable";
        String filename = getProperties().getProperty(sitesavailable, "/etc/apache2/sites-available/")
                + domain + ".conf";
        String command = "sudo a2dissite " + domain + ".conf; sudo /etc/init.d/apache2 reload; rm "
                + filename;
        execute(command);
    }

    @Override
    public void enableDomain(final String domain) {
        String command = "sudo a2ensite " + domain + ".conf && sudo /etc/init.d/apache2 reload";
        execute(command);
    }

    @Override
    public void disableDomain(final String domain) {
        String command = "sudo a2dissite " + domain + ".conf && sudo /etc/init.d/apache2 reload";
        execute(command);
    }

    @Override
    public List<String> getResources() {
        return getResources(null, null);
    }

    @Override
    public List<String> getResources(final String domain) {
        return getResources(domain, null);
    }

    @Override
    public List<String> getResources(final String domain, final String type) {
        Set<String> fileNames = new HashSet<>();
        try {
            String[] domains = addDomains(domain);
            ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
            channel.connect();
            Stack<String> domainPathes = buildDomainPathes(channel);

            for (String curDomain : domains) {
                if (domainShouldBeProcessed(curDomain, domainPathes)) {
                    String path;
                    if(type != null && !type.isEmpty()) {
                      path = ROOT_DIR + curDomain + "/" + type;
                    } else {
                       path = ROOT_DIR + curDomain;
                    }
                    LOGGER.debug("listing resources for " + path);
                    fileNames.addAll(readFilenames(channel, path, domain));
                }
            }
            channel.disconnect();
        } catch (JSchException | SftpException ex) {
            LOGGER.error("error while listing available resources", ex);
        }

        return new ArrayList<>(fileNames);
    }

    private String[] addDomains(final String domain) {
        String[] domains;
        if (domain != null && !domain.isEmpty()) {
            domains = new String[]{domain};
        } else {
            domains = getDomains(WebserverConnectorService.DOMAIN_FILTER_ALL);
        }
        return domains;
    }

    private Stack<String> buildDomainPathes(final ChannelSftp channel) throws SftpException {
        Stack<String> domainPaths = new Stack();
        String rootPath = ROOT_DIR;
        for (Object file : channel.ls(rootPath)) {
            String filename = getFilenameForLs(file.toString());
            domainPaths.push(filename);
        }
        return domainPaths;
    }

    private boolean domainShouldBeProcessed(final String domain, final Stack<String> domainPathes) {
        for (String domainPath : domainPathes) {
            if (domainPath.equals(domain)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> readFilenames(final ChannelSftp channel, final String path, final String domain) {
        Set<String> fileNames =  new HashSet<>();
        try {
            for (Object file : channel.ls(path)) {
                String filename = getFilenameForLs(file.toString());
                if (!filename.equals(".") && !filename.equals("..")
                        && !filename.equals("wp") && !filename.equals("img")) {
                    File f = new File(path + "/" + filename);
                    if (f.isDirectory()) {
                        fileNames.addAll(readFilenames(channel, path + "/" + filename, domain));

                    } else if (f.isFile()) {
                        String qualifiedFilename = path + "/" + filename;
                        qualifiedFilename = qualifiedFilename.replaceAll(ROOT_DIR + domain + "/", "");
                        fileNames.add(qualifiedFilename);
                    }
                }
            }
        } catch (SftpException ex) {
            LOGGER.error("ApacheSSHConnectorService.readFilenames", ex);
        }
        return fileNames;
    }

    @Override
    public void createImageForCmfBinaryContent(final InputStream src, final String resourceName)
            throws JSchException, SftpException {
        String folder = RESOURCE_IMG_FOLDER + "/" + resourceName.substring(0, resourceName.lastIndexOf('/'));
        String command = "mkdir -p " + folder;
        execute(command);
        ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
        channel.connect();

        String path = RESOURCE_IMG_FOLDER + "/" + resourceName;
        channel.put(src, path);
        channel.disconnect();
    }

    @Override
    public void createResource(final String domain, final String resourceName, final InputStream src)
            throws JSchException, SftpException {
        createResource(domain, null, resourceName, src);
    }

    @Override
    public void createResource(final String domain, final String uploadPath, final String resourceName,
            final InputStream src) throws JSchException, SftpException {
        String folder = checkFolderEnding(ROOT_DIR + domain);
        if (uploadPath != null && !uploadPath.isEmpty()) {
            folder = checkFolderEnding(folder + uploadPath);
        }
        createDirectoryForResource(folder);
        ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
        channel.connect();
        String path = folder + resourceName;
        channel.put(src, path);
        channel.disconnect();

    }

    private void createDirectoryForResource(final String folder)
            throws JSchException {
        String command = "mkdir -p " + folder;
        execute(command);
        ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
        channel.connect();

    }

    @Override
    public void deleteResource(final String domain, final String resourceName)
            throws JSchException, SftpException {
        deleteResource(domain, null, resourceName);
    }

    @Override
    public void deleteResource(final String domain, final String deletePath, final String resourceName)
            throws JSchException, SftpException {
        ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
        channel.connect();
        String folder = checkFolderEnding(ROOT_DIR + domain);
        if (deletePath != null && !deletePath.isEmpty()) {
            folder = checkFolderEnding(folder + deletePath);
        }
        String path = folder + resourceName;
        channel.rm(path);
        channel.disconnect();
    }

    @Override
    public void copyResources(final String sourceDomain, final String destinationDomain) throws JSchException {
        String sourcePath = ROOT_DIR + sourceDomain + "/";
        String destinationPath = ROOT_DIR + destinationDomain + "/";
        String destinationDirectory = destinationPath.substring(0, destinationPath.lastIndexOf("/"));
        createDirectoryForResource(destinationDirectory);
        String command = "cp -R " + sourcePath + "* " + destinationPath;
        execute(command);
    }

    @Override
    public void copySingleResource(final String sourceDomain, final String destinationDomain,
            final String resourceName) throws JSchException {
        copySingleResource(sourceDomain, destinationDomain, null, resourceName);
    }

    @Override
    public void copySingleResource(final String sourceDomain, final String destinationDomain,
            final String copyPath, final String resourceName) throws JSchException {
        String sourcePath = checkFolderEnding(ROOT_DIR + sourceDomain);
        String destinationPath = checkFolderEnding(ROOT_DIR + destinationDomain);
        if (copyPath != null && !copyPath.isEmpty()) {
            String additionalPath = checkFolderEnding(copyPath);
            sourcePath = sourcePath + additionalPath;
            destinationPath = destinationPath + additionalPath;
        }
        sourcePath = sourcePath + resourceName;
        destinationPath = destinationPath + resourceName;
        String destinationDirectory = destinationPath.substring(0, destinationPath.lastIndexOf("/"));
        createDirectoryForResource(destinationDirectory);
        String command = "cp -R " + sourcePath + " " + destinationPath;
        execute(command);
    }

    private String checkFolderEnding(final String folder) {
        if (!folder.endsWith("/")) {
            return folder + "/";
        }
        return folder;
    }

    @Override
    public byte[] readStreamForWebserverFile(final String path, final String resourceName) {
        byte[] data = null;
        InputStream stream = null;
        ChannelSftp channel = null;
        try {
            channel = (ChannelSftp) getSession().openChannel("sftp");

            channel.connect();
            String folder = checkFolderEnding(path);
            stream = channel.get(ROOT_DIR + folder + resourceName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read = 0;
            while ((read = stream.read(buffer, 0, buffer.length)) != -1) {
                baos.write(buffer, 0, read);
            }
            baos.flush();
            data = baos.toByteArray();
            baos.close();
            stream.close();
            channel.disconnect();
        } catch (JSchException | SftpException ex) {
            LOGGER.warn("ApacheSSHConnectorService.readWebserverResource - no such file: " + path + resourceName);
        } catch (IOException ex) {
            LOGGER.warn("ApacheSSHConnectorService.readWebserverResource", ex);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
        return data;
    }
}
