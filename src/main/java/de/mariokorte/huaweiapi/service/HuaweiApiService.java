package de.mariokorte.huaweiapi.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class HuaweiApiService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RateLimitService rateLimitService;

    private static final String BASE_URL = "https://eu5.fusionsolar.huawei.com";
    private static final String LOGIN_URL = BASE_URL + "/thirdData/login";
    private static final String PLANTS_URL = BASE_URL + "/thirdData/stations";
    private static final String DEVICES_URL = BASE_URL + "/thirdData/getDevList";
    private static final String METER_DATA_URL = BASE_URL + "/thirdData/getDevRealKpi";

    private String xsrfToken;
    private String meterDeviceId;
    private String inverterDeviceId;
    private String plantCode;

    private String login(String username, String password) {
        if (rateLimitService.isRateLimited()) {
            throw new RuntimeException("Rate limited. Please wait before retrying.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        String body = "{\"userName\":\"" + username + "\", \"systemCode\":\"" + password + "\"}";

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(LOGIN_URL, request, String.class);

        handleRateLimiting(response);

        String token = response.getHeaders().getFirst("xsrf-token");
        rateLimitService.cacheToken(token);
        return token;
    }

    @Cacheable(value = "householdActivePowerCache", key = "#username", cacheManager = "cacheManager")
    public double getHouseholdActivePower(String username, String password) {
       double meterPower = getMeterActivePower(username, password);
       double inverterPower = getInvActivePower(username, password);

       return inverterPower - meterPower;
    }

    @Cacheable(value = "meterActivePowerCache", key = "#username", cacheManager = "cacheManager")
    public double getMeterActivePower(String username, String password) {
        if (rateLimitService.isRateLimited()) {
            throw new RuntimeException("Rate limited. Please wait before retrying.");
        }

        // Step 1: Get or Refresh Token
        if (xsrfToken == null) {
            xsrfToken = login(username, password);
        }

        String devId = null;
        try {
            // Step 2: Get List of Plants
            String plantCode = getPlantCode(username, password);

            // Step 3: Get Plant Devices
            devId = getMeterDeviceId(username, password);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // Step 4: Get Meter Data
        String meterBody = "{\"devIds\": \"" + devId + "\", \"devTypeId\": \"47\"}";

        HttpEntity<String> meterRequest = new HttpEntity<>(meterBody, getHttpHeaders());
        ResponseEntity<String> meterResponse = restTemplate.exchange(METER_DATA_URL, HttpMethod.POST, meterRequest, String.class);
        if (handleRateLimitingOrTokenRefresh(meterResponse, username, password)) {
            return getMeterActivePower(username, password); // Retry after token refresh
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode meterRoot = mapper.readTree(meterResponse.getBody());

            return meterRoot.path("data").get(0).path("dataItemMap").path("active_power").asDouble();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Cacheable(value = "invActivePowerCache", key = "#username", cacheManager = "cacheManager")
    public double getInvActivePower(String username, String password) {
        if (rateLimitService.isRateLimited()) {
            throw new RuntimeException("Rate limited. Please wait before retrying.");
        }

        // Step 1: Get or Refresh Token
        if (xsrfToken == null) {
            xsrfToken = login(username, password);
        }

        String devId = null;
        try {
            // Step 2: Get List of Plants
            String plantCode = getPlantCode(username, password);

            // Step 3: Get Plant Devices
            devId = getInverterDeviceId(username, password);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // Step 4: Get Inverter Data
        String inverterBody = "{\"devIds\": \"" + devId + "\", \"devTypeId\": \"1\"}";

        HttpEntity<String> meterRequest = new HttpEntity<>(inverterBody, getHttpHeaders());
        ResponseEntity<String> meterResponse = restTemplate.exchange(METER_DATA_URL, HttpMethod.POST, meterRequest, String.class);
        if (handleRateLimitingOrTokenRefresh(meterResponse, username, password)) {
            return getInvActivePower(username, password); // Retry after token refresh
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode meterRoot = mapper.readTree(meterResponse.getBody());

            return 1000 * meterRoot.path("data").get(0).path("dataItemMap").path("active_power").asDouble();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String getMeterDeviceId(String username, String password) throws JsonProcessingException {
        if (this.meterDeviceId != null && !this.meterDeviceId.isEmpty()) {
            return this.meterDeviceId;
        }

        String devicesBody = "{\"stationCodes\": \"" + this.plantCode + "\"}";

        HttpEntity<String> devicesRequest = new HttpEntity<>(devicesBody, getHttpHeaders());
        ResponseEntity<String> devicesResponse = restTemplate.exchange(DEVICES_URL, HttpMethod.POST, devicesRequest, String.class);
        if (handleRateLimitingOrTokenRefresh(devicesResponse, username, password)) {
            return getMeterDeviceId(username, password); // Retry after token refresh
        }

        JsonNode devicesList = null;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode devicesRoot = mapper.readTree(devicesResponse.getBody());

        devicesList = devicesRoot.path("data");

        for (JsonNode device : devicesList) {
            if (device.path("devTypeId").asInt() == 47) {
                this.meterDeviceId = device.path("id").asText();
                continue;
            } else if (device.path("devTypeId").asInt() == 1) {
                this.inverterDeviceId = device.path("id").asText();
                continue;
            }
        }

        if (this.meterDeviceId == null) {
            throw new RuntimeException("No meter device found");
        }
        return this.meterDeviceId;
    }

    private String getInverterDeviceId(String username, String password) throws JsonProcessingException {
        if (this.inverterDeviceId != null && !this.inverterDeviceId.isEmpty()) {
            return this.inverterDeviceId;
        }

        String devicesBody = "{\"stationCodes\": \"" + this.plantCode + "\"}";

        HttpEntity<String> devicesRequest = new HttpEntity<>(devicesBody, getHttpHeaders());
        ResponseEntity<String> devicesResponse = restTemplate.exchange(DEVICES_URL, HttpMethod.POST, devicesRequest, String.class);
        if (handleRateLimitingOrTokenRefresh(devicesResponse, username, password)) {
            return getInverterDeviceId(username, password); // Retry after token refresh
        }

        JsonNode devicesList = null;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode devicesRoot = mapper.readTree(devicesResponse.getBody());


        devicesList = devicesRoot.path("data");

        for (JsonNode device : devicesList) {
            if (device.path("devTypeId").asInt() == 47) {
                this.meterDeviceId = device.path("id").asText();
                continue;
            } else if (device.path("devTypeId").asInt() == 1) {
                this.inverterDeviceId = device.path("id").asText();
                continue;
            }
        }

        if (this.inverterDeviceId == null) {
            throw new RuntimeException("No meter device found");
        }
        return this.inverterDeviceId;
    }

    private String getPlantCode(String username, String password) throws JsonProcessingException {
        if (this.plantCode != null && !this.plantCode.isEmpty()) {
            return this.plantCode;
        }

        String plantsBody = "{\"pageNo\": 1, \"pageSize\": 100}";

        HttpEntity<String> plantsRequest = new HttpEntity<>(plantsBody, getHttpHeaders());
        ResponseEntity<String> plantsResponse = restTemplate.exchange(PLANTS_URL, HttpMethod.POST, plantsRequest, String.class);
        if (handleRateLimitingOrTokenRefresh(plantsResponse, username, password)) {
            return getPlantCode(username, password); // Retry after token refresh
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode plantsRoot = mapper.readTree(plantsResponse.getBody());
        this.plantCode = plantsRoot.path("data").path("list").get(0).path("plantCode").asText();

        return this.plantCode;
    }

    private HttpHeaders getHttpHeaders() {
        if (xsrfToken != null) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("xsrf-token", xsrfToken);
            headers.set("Content-Type", "application/json");
            return headers;
        } else {
            throw new RuntimeException();
        }
    }


    private void handleRateLimiting(ResponseEntity<String> response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());
            if (root.path("failCode").asInt() == 407) {
                rateLimitService.setRateLimit();
                throw new RuntimeException("Rate limited. Please wait before retrying.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean handleRateLimitingOrTokenRefresh(ResponseEntity<String> response, String username, String password) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());

            int failCode = root.path("failCode").asInt();
            if (failCode == 407) {
                rateLimitService.setRateLimit();
                throw new RuntimeException("Rate limited. Please wait before retrying.");
            } else if (failCode == 305) {
                // Token expired or invalid, refresh it
                rateLimitService.evictToken();
                xsrfToken = login(username, password);
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}