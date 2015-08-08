package io.kofrezo.webserverconnector.interfaces;

public interface WebserverService
{
    public String[] getVirtualHosts();
    
    public String[] getVirtualHostsEnabled();
    
    public String[] getVirtualHostsDisabled();
    
    public void createVirtualHost(String domain, String[] aliases);
    
    public void deleteVirtualHost(String domain);
    
    public void enableVirtualHost(String domain);
    
    public void disableVirtualHost(String domain);
    
    public boolean isSSLEnabled(String domain);
}
