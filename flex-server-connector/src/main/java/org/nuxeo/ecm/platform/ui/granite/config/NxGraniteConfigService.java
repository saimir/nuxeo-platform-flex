package org.nuxeo.ecm.platform.ui.granite.config;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.granite.config.GraniteConfig;
import org.granite.config.flex.Adapter;
import org.granite.config.flex.Channel;
import org.granite.config.flex.Destination;
import org.granite.config.flex.EndPoint;
import org.granite.config.flex.Factory;
import org.granite.config.flex.Service;
import org.granite.config.flex.ServicesConfig;
import org.granite.context.GraniteContext;
import org.granite.context.SimpleGraniteContext;
import org.granite.messaging.webapp.HttpGraniteContext;
import org.jboss.seam.mock.MockServletContext;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Extension;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.restlet.Context;

public class NxGraniteConfigService extends DefaultComponent implements
        NxGraniteConfigManager{//, FrameworkListener {

    public static final ComponentName NAME = new ComponentName(
            "org.nuxeo.ecm.platform.ui.granite.config.NxGraniteConfigService");

    public static final Log log = LogFactory.getLog(NxGraniteConfigService.class);

    public static final String CLASS_NAME = "flex.messaging.services.RemotingService";

    public static final String MESSAGE_TYPES = "flex.messaging.messages.RemotingMessage";

    public static final String SEAM_FACTORY_CLASS = "org.nuxeo.ecm.platform.ui.granite.factory.NuxeoSeamServiceFactory";

    public static final String RUNTIME_FACTORY_CLASS = "org.nuxeo.ecm.platform.ui.granite.factory.NuxeoRuntimeServiceFactory";

    public static final String CHANNEL = "nx-amf";

    public static final String CHANNEL_CLASS = "mx.messaging.channels.AMFChannel";

    public static final String ENDPOINT = "http://{server.name}:{server.port}/nuxeo/nuxeo-amf/amf";

    public static final String ENDPOINT_CLASS = "flex.messaging.endpoints.AMFEndpoint";

    public static final String SEAM_FACTORY = "seamFactory";

    public static final String RUNTIME_FACTORY = "nxruntimeFactory";

    private List<String> channelRef = new LinkedList<String>();

    private HashMap<String, Service> servicesMap = new HashMap<String, Service>();

    @Override
    public void registerExtension(Extension extension) throws Exception {
        Object[] contributions = extension.getContributions();
        if (contributions != null) {
            if (extension.getExtensionPoint().equals("services")) {
                for (Object contribution : contributions) {
                    Service currentService = null;
                    if (contribution instanceof RuntimeComponent) {
                        currentService = createService((RuntimeComponent) contribution);
                    } else if (contribution instanceof SeamComponent) {
                        currentService = createService((SeamComponent) contribution);
                    }
                    servicesMap.put(currentService.getId(), currentService);
                    log.info("Registering new service: "
                            + currentService.getId());
                }
            }
        }
    }

    public NxGraniteConfigService() {
        channelRef.add(CHANNEL);
    }

    public Service createService(RuntimeComponent component) {
        String destinationId = "";
        if (component.getDestinationId() == null) {
            destinationId = component.getId();
        } else if (component.getDestinationId().equals("")) {
            destinationId = component.getId();
        } else {
            destinationId = component.getDestinationId();
        }
        Map<String, Serializable> properties = new HashMap<String, Serializable>();
        properties.put("class", component.getClassName());
        properties.put("factory", RUNTIME_FACTORY);
        HashMap<String, Destination> destinations = new HashMap<String, Destination>();
        Destination destination = createDestination(destinationId, properties);
        destinations.put(destination.getId(), destination);
        return new Service(component.getId(), CLASS_NAME, MESSAGE_TYPES, null,
                new HashMap<String, Adapter>(), destinations);
    }

    public Service createService(SeamComponent component) {
        String destinationId = "";
        if (component.getDestinationId() == null) {
            destinationId = component.getId();
        } else if (component.getDestinationId().equals("")) {
            destinationId = component.getId();
        } else {
            destinationId = component.getDestinationId();
        }
        Map<String, Serializable> properties = new HashMap<String, Serializable>();
        if (component.getSource() == null) {
            properties.put("source", component.getId());
        } else if (component.getSource().equals("")) {
            properties.put("source", component.getId());
        } else {
            properties.put("source", component.getSource());
        }
        properties.put("factory", SEAM_FACTORY);
        HashMap<String, Destination> destinations = new HashMap<String, Destination>();
        Destination destination = createDestination(destinationId, properties);
        destinations.put(destination.getId(), destination);
        return new Service(component.getId(), CLASS_NAME, MESSAGE_TYPES, null,
                new HashMap<String, Adapter>(), destinations);
    }

    public Destination createDestination(String id,
            Map<String, Serializable> properties) {
        return new Destination(id, channelRef, properties, null, null);
    }

    @Override
    public void unregisterExtension(Extension extension) throws Exception {
        super.unregisterExtension(extension);
        Object[] contributions = extension.getContributions();
        if (contributions != null) {
            if (extension.getExtensionPoint().equals("services")) {
                for (Object contribution : contributions) {
                    if (contribution instanceof RuntimeComponent) {
                        servicesMap.remove(((RuntimeComponent) contribution).getId());
                    } else if (contribution instanceof SeamComponent) {
                        servicesMap.remove(((SeamComponent) contribution).getId());
                    }
                }
            }
        }
    }

    public Collection<Service> getServicesMap() {
        return servicesMap.values();
    }
    //This code should have set the granite services configuration, but GraniteContext.getinstance return null.
    //Hack is to set the configuration in a Custom AMFMessageFilter -> NxAMFMessageFilter
/*
    public Factory getSeamFactory() {
        return new Factory(SEAM_FACTORY, SEAM_FACTORY_CLASS, mockMap);
    }

    public Factory getNxRuntimeFactory() {
        return new Factory(RUNTIME_FACTORY, RUNTIME_FACTORY_CLASS, mockMap);
    }

    public Channel getNxChannel() {
        return new Channel(CHANNEL, CHANNEL_CLASS, new EndPoint(ENDPOINT,
                ENDPOINT_CLASS), mockMap);
    }

    public void frameworkEvent(FrameworkEvent event) {
        if (event.getType() == FrameworkEvent.STARTED) {

            ClassLoader jbossCL = Thread.currentThread().getContextClassLoader();
            ClassLoader nuxeoCL = NxGraniteConfigService.class.getClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(nuxeoCL);
                log.info("GraniteDS configuration registering");
                //Can't instanciate
                try{
                MockServletContext sc = new MockServletContext();
                sc.setAttribute(ServicesConfig.class.getName() + "_CACHE", null);
              
                HttpGraniteContext.createThreadIntance(GraniteConfig.loadConfig(sc), ServicesConfig.loadConfig(sc), null, null, null);
                }catch (Exception e){
                    
                }
                // Add SeamFactory and RuntimeFactory
                GraniteContext.getCurrentInstance().getServicesConfig().addFactory(
                        getSeamFactory());
                GraniteContext.getCurrentInstance().getServicesConfig().addFactory(
                        getNxRuntimeFactory());
                // Add Nuxeo Channel               
                GraniteContext.getCurrentInstance().getServicesConfig().addChannel(
                        getNxChannel());
                Collection<Service> services = getServicesMap();
                for (Service service : services) {
                    GraniteContext.getCurrentInstance().getServicesConfig().addService(
                            service);
                }
            } finally {
                Thread.currentThread().setContextClassLoader(jbossCL);
                log.debug("JBoss ClassLoader restored");
            }
        }

    }

    @Override
    public void activate(ComponentContext context) throws Exception {
        if (Boolean.parseBoolean(Framework.getProperty(
                "org.nuxeo.ecm.platform.relations.initOnStartup", "true"))) {
            context.getRuntimeContext().getBundle().getBundleContext().addFrameworkListener(
                    this);
        }
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        // this is doing nothing if listener was not registered
        context.getRuntimeContext().getBundle().getBundleContext().removeFrameworkListener(
                this);
    }
*/
}
