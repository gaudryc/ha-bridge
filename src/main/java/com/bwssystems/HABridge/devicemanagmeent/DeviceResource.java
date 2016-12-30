package com.bwssystems.HABridge.devicemanagmeent;

import static spark.Spark.get;
import static spark.Spark.options;
import static spark.Spark.post;
import static spark.Spark.put;
import static spark.Spark.delete;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bwssystems.HABridge.BridgeSettingsDescriptor;
import com.bwssystems.HABridge.DeviceMapTypes;
import com.bwssystems.HABridge.HomeManager;
import com.bwssystems.HABridge.dao.BackupFilename;
import com.bwssystems.HABridge.dao.DeviceDescriptor;
import com.bwssystems.HABridge.dao.DeviceRepository;
import com.bwssystems.HABridge.dao.ErrorMessage;
import com.bwssystems.HABridge.util.JsonTransformer;
import com.google.gson.Gson;

/**
	spark core server for bridge configuration
 */
public class DeviceResource {
    private static final String API_CONTEXT = "/api/devices";
    private static final Logger log = LoggerFactory.getLogger(DeviceResource.class);
    private DeviceRepository deviceRepository;
    private HomeManager homeManager;
    private static final Set<String> supportedVerbs = new HashSet<>(Arrays.asList("get", "put", "post"));

	public DeviceResource(BridgeSettingsDescriptor theSettings, HomeManager aHomeManager) {
		this.deviceRepository = new DeviceRepository(theSettings.getUpnpDeviceDb());
		homeManager = aHomeManager;
		setupEndpoints();
	}

	public DeviceRepository getDeviceRepository() {
		return deviceRepository;
	}

    private void setupEndpoints() {
    	log.info("HABridge device management service started.... ");
	    // http://ip_address:port/api/devices CORS request
	    options(API_CONTEXT, "application/json", (request, response) -> {
	        response.status(HttpStatus.SC_OK);
	        response.header("Access-Control-Allow-Origin", request.headers("Origin"));
	        response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
	        response.header("Access-Control-Allow-Headers", request.headers("Access-Control-Request-Headers"));
	        response.header("Content-Type", "text/html; charset=utf-8");
	    	return "";
	    });
    	post(API_CONTEXT, "application/json", (request, response) -> {
	    	log.debug("Create a Device(s) - request body: " + request.body());
	    	DeviceDescriptor devices[];
	    	if(request.body().substring(0,1).equalsIgnoreCase("[") == true) {
	    		devices = new Gson().fromJson(request.body(), DeviceDescriptor[].class);
	    	}
	    	else {
	    		devices = new Gson().fromJson("[" + request.body() + "]", DeviceDescriptor[].class);
	    	}
	    	for(int i = 0; i < devices.length; i++) {
		    	if(devices[i].getContentBody() != null ) {
		            if (devices[i].getContentType() == null || devices[i].getHttpVerb() == null || !supportedVerbs.contains(devices[i].getHttpVerb().toLowerCase())) {
		            	response.status(HttpStatus.SC_BAD_REQUEST);
						log.debug("Bad http verb in create a Device(s): " + request.body());
						return new ErrorMessage("Bad http verb in create a Device(s): " + request.body() + " ");
		            }
		        }
	    	}

	    	deviceRepository.save(devices);
			log.debug("Created a Device(s): " + request.body());

	        response.header("Access-Control-Allow-Origin", request.headers("Origin"));
			response.status(HttpStatus.SC_CREATED);

            return devices;
	    }, new JsonTransformer());

	    // http://ip_address:port/api/devices/:id CORS request
	    options(API_CONTEXT + "/:id", "application/json", (request, response) -> {
	        response.status(HttpStatus.SC_OK);
	        response.header("Access-Control-Allow-Origin", request.headers("Origin"));
	        response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
	        response.header("Access-Control-Allow-Headers", request.headers("Access-Control-Request-Headers"));
	        response.header("Content-Type", "text/html; charset=utf-8");
	    	return "";
	    });
    	put (API_CONTEXT + "/:id", "application/json", (request, response) -> {
	    	log.debug("Edit a Device - request body: " + request.body());
        	DeviceDescriptor device = new Gson().fromJson(request.body(), DeviceDescriptor.class);
	        if(deviceRepository.findOne(request.params(":id")) == null){
		    	log.debug("Could not save an edited device, Device Id not found: " + request.params(":id"));
		    	response.status(HttpStatus.SC_BAD_REQUEST);
		    	return new ErrorMessage("Could not save an edited device, Device Id not found: " + request.params(":id") + " ");
	        }
	        else
	        {
				log.debug("Saving an edited Device: " + device.getName());

				if (device.getDeviceType() != null)
					device.setDeviceType(device.getDeviceType());

				DeviceDescriptor[] theDevices = new DeviceDescriptor[1];
				theDevices[0] = device;
				deviceRepository.save(theDevices);
				response.status(HttpStatus.SC_OK);
	        }
	        return device;
    	}, new JsonTransformer());

    	get (API_CONTEXT, "application/json", (request, response) -> {
    		List<DeviceDescriptor> deviceList = deviceRepository.findAll();
	    	log.debug("Get all devices");
	    	JsonTransformer aRenderer = new JsonTransformer();
	    	String theStream = aRenderer.render(deviceList);
	    	log.debug("The Device List: " + theStream);
			response.status(HttpStatus.SC_OK);
    		return deviceList;
    	}, new JsonTransformer());

    	get (API_CONTEXT + "/:id", "application/json", (request, response) -> {
	    	log.debug("Get a device");
	        DeviceDescriptor descriptor = deviceRepository.findOne(request.params(":id"));
	        if(descriptor == null) {
				response.status(HttpStatus.SC_NOT_FOUND);
				return new ErrorMessage("Could not find, id: " + request.params(":id") + " ");
	        }
	        else
	        	response.status(HttpStatus.SC_OK);
	        return descriptor;
	    }, new JsonTransformer());

    	delete (API_CONTEXT + "/:id", "application/json", (request, response) -> {
    		String anId = request.params(":id");
	    	log.debug("Delete a device: " + anId);
	        DeviceDescriptor deleted = deviceRepository.findOne(anId);
	        if(deleted == null) {
				response.status(HttpStatus.SC_NOT_FOUND);
				return new ErrorMessage("Could not delete, id: " + anId + " not found. ");
	        }
	        else
	        {
	        	deviceRepository.delete(deleted);
				response.status(HttpStatus.SC_OK);
	        }
	        return null;
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/vera/devices", "application/json", (request, response) -> {
	    	log.debug("Get vera devices");
        	response.status(HttpStatus.SC_OK);
	        return homeManager.findResource(DeviceMapTypes.VERA_DEVICE[DeviceMapTypes.typeIndex]).getItems(DeviceMapTypes.VERA_DEVICE[DeviceMapTypes.typeIndex]);
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/vera/scenes", "application/json", (request, response) -> {
	    	log.debug("Get vera scenes");
	        response.status(HttpStatus.SC_OK);
	        return homeManager.findResource(DeviceMapTypes.VERA_DEVICE[DeviceMapTypes.typeIndex]).getItems(DeviceMapTypes.VERA_SCENE[DeviceMapTypes.typeIndex]);
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/harmony/activities", "application/json", (request, response) -> {
	    	log.debug("Get harmony activities");
	      	response.status(HttpStatus.SC_OK);
	      	return homeManager.findResource(DeviceMapTypes.HARMONY_ACTIVITY[DeviceMapTypes.typeIndex]).getItems(DeviceMapTypes.HARMONY_ACTIVITY[DeviceMapTypes.typeIndex]);
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/harmony/show", "application/json", (request, response) -> {
	    	log.debug("Get harmony current activity");
      		return homeManager.findResource(DeviceMapTypes.HARMONY_ACTIVITY[DeviceMapTypes.typeIndex]).getItems("current_activity");
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/harmony/devices", "application/json", (request, response) -> {
	    	log.debug("Get harmony devices");
	      	response.status(HttpStatus.SC_OK);
	      	return homeManager.findResource(DeviceMapTypes.HARMONY_BUTTON[DeviceMapTypes.typeIndex]).getItems(DeviceMapTypes.HARMONY_BUTTON[DeviceMapTypes.typeIndex]);
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/nest/items", "application/json", (request, response) -> {
	    	log.debug("Get nest items");
	      	response.status(HttpStatus.SC_OK);
	      	return homeManager.findResource(DeviceMapTypes.NEST_HOMEAWAY[DeviceMapTypes.typeIndex]).getItems(DeviceMapTypes.NEST_HOMEAWAY[DeviceMapTypes.typeIndex]);
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/hue/devices", "application/json", (request, response) -> {
	    	log.debug("Get hue items");
	      	response.status(HttpStatus.SC_OK);
	      	return homeManager.findResource(DeviceMapTypes.HUE_DEVICE[DeviceMapTypes.typeIndex]).getItems(DeviceMapTypes.HUE_DEVICE[DeviceMapTypes.typeIndex]);
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/hal/devices", "application/json", (request, response) -> {
	    	log.debug("Get hal items");
	      	response.status(HttpStatus.SC_OK);
	      	return homeManager.findResource(DeviceMapTypes.HAL_DEVICE[DeviceMapTypes.typeIndex]).getItems(DeviceMapTypes.HAL_DEVICE[DeviceMapTypes.typeIndex]);
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/mqtt/devices", "application/json", (request, response) -> {
	    	log.debug("Get MQTT brokers");
	      	response.status(HttpStatus.SC_OK);
	      	return homeManager.findResource(DeviceMapTypes.MQTT_MESSAGE[DeviceMapTypes.typeIndex]).getItems(DeviceMapTypes.MQTT_MESSAGE[DeviceMapTypes.typeIndex]);
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/hass/devices", "application/json", (request, response) -> {
	    	log.debug("Get HomeAssistant Clients");
	      	response.status(HttpStatus.SC_OK);
	      	return homeManager.findResource(DeviceMapTypes.HASS_DEVICE[DeviceMapTypes.typeIndex]).getItems(DeviceMapTypes.HASS_DEVICE[DeviceMapTypes.typeIndex]);
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/map/types", "application/json", (request, response) -> {
	    	log.debug("Get map types");
	      	return new DeviceMapTypes().getDeviceMapTypes();
	    }, new JsonTransformer());

	    // http://ip_address:port/api/devices/exec/renumber CORS request
	    options(API_CONTEXT + "/exec/renumber", "application/json", (request, response) -> {
	        response.status(HttpStatus.SC_OK);
	        response.header("Access-Control-Allow-Origin", request.headers("Origin"));
	        response.header("Access-Control-Allow-Methods", "POST");
	        response.header("Access-Control-Allow-Headers", request.headers("Access-Control-Request-Headers"));
	        response.header("Content-Type", "text/html; charset=utf-8");
	    	return "";
	    });
    	post (API_CONTEXT + "/exec/renumber", "application/json", (request, response) -> {
	    	log.debug("Renumber devices.");
	    	deviceRepository.renumber();
	        return null;
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/backup/available", "application/json", (request, response) -> {
        	log.debug("Get backup filenames");
          	response.status(HttpStatus.SC_OK);
          	return deviceRepository.getBackups();
        }, new JsonTransformer());

	    // http://ip_address:port/api/devices/backup/create CORS request
	    options(API_CONTEXT + "/backup/create", "application/json", (request, response) -> {
	        response.status(HttpStatus.SC_OK);
	        response.header("Access-Control-Allow-Origin", request.headers("Origin"));
	        response.header("Access-Control-Allow-Methods", "PUT");
	        response.header("Access-Control-Allow-Headers", request.headers("Access-Control-Request-Headers"));
	        response.header("Content-Type", "text/html; charset=utf-8");
	    	return "";
	    });
    	put (API_CONTEXT + "/backup/create", "application/json", (request, response) -> {
	    	log.debug("Create backup: " + request.body());
        	BackupFilename aFilename = new Gson().fromJson(request.body(), BackupFilename.class);
        	BackupFilename returnFilename = new BackupFilename();
        	returnFilename.setFilename(deviceRepository.backup(aFilename.getFilename()));
	        return returnFilename;
    	}, new JsonTransformer());

	    // http://ip_address:port/api/devices/backup/delete CORS request
	    options(API_CONTEXT + "/backup/delete", "application/json", (request, response) -> {
	        response.status(HttpStatus.SC_OK);
	        response.header("Access-Control-Allow-Origin", request.headers("Origin"));
	        response.header("Access-Control-Allow-Methods", "POST");
	        response.header("Access-Control-Allow-Headers", request.headers("Access-Control-Request-Headers"));
	        response.header("Content-Type", "text/html; charset=utf-8");
	    	return "";
	    });
    	post (API_CONTEXT + "/backup/delete", "application/json", (request, response) -> {
	    	log.debug("Delete backup: " + request.body());
        	BackupFilename aFilename = new Gson().fromJson(request.body(), BackupFilename.class);
        	if(aFilename != null)
        		deviceRepository.deleteBackup(aFilename.getFilename());
        	else
        		log.warn("No filename given for delete backup.");
	        return null;
	    }, new JsonTransformer());

	    // http://ip_address:port/api/devices/backup/restore CORS request
	    options(API_CONTEXT + "/backup/restore", "application/json", (request, response) -> {
	        response.status(HttpStatus.SC_OK);
	        response.header("Access-Control-Allow-Origin", request.headers("Origin"));
	        response.header("Access-Control-Allow-Methods", "POST");
	        response.header("Access-Control-Allow-Headers", request.headers("Access-Control-Request-Headers"));
	        response.header("Content-Type", "text/html; charset=utf-8");
	    	return "";
	    });
    	post (API_CONTEXT + "/backup/restore", "application/json", (request, response) -> {
	    	log.debug("Restore backup: " + request.body());
        	BackupFilename aFilename = new Gson().fromJson(request.body(), BackupFilename.class);
        	if(aFilename != null) {
        		deviceRepository.restoreBackup(aFilename.getFilename());
        		deviceRepository.loadRepository();
        	}
        	else
        		log.warn("No filename given for restore backup.");
	        return null;
	    }, new JsonTransformer());
    }
}