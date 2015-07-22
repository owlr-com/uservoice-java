package com.uservoice;

import java.util.HashMap;
import java.util.Map;
import org.scribe.builder.ServiceBuilder;
import org.scribe.model.OAuthConstants;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Client {

	private String serverLocation;
	private OAuthService service;
	private Token requestToken;
    private Token token;


    public Client(String subdomainName, String apiKey) {
        this(subdomainName, apiKey, null);
    }

    public Client(String subdomainName, String apiKey, String apiSecret) {
        this(subdomainName, apiKey, apiSecret, null);
    }

    public Client(String subdomainName, String apiKey, String apiSecret, String callback) {
        this(subdomainName, apiKey, apiSecret, callback, "", "");
    }

    public Client(String subdomainName, String apiKey, String apiSecret, String callback, String token, String secret) {
        this(subdomainName, apiKey, apiSecret, callback, token, secret, "uservoice.com");
    }

    public Client(String subdomainName, String apiKey, String apiSecret, String callback, String token, String secret, String uservoiceDomain) {
        this(subdomainName, apiKey, apiSecret, callback, token, secret, uservoiceDomain, "https");
    }

    public Client(String subdomainName, String apiKey, String apiSecret, String callback, String token, String secret, String uservoiceDomain, String protocol) {
        this(getValueOrDefault(protocol, "https") + "://" + subdomainName + "."
                + getValueOrDefault(uservoiceDomain, "uservoice.com"), new ServiceBuilder()
                .provider(
                        new UserVoiceApi(getValueOrDefault(protocol, "https") + "://" + subdomainName + "."
                                + getValueOrDefault(uservoiceDomain, "uservoice.com"))).apiKey(apiKey)
                .apiSecret(getValueOrDefault(apiSecret, apiKey))
                .callback(getValueOrDefault(callback, OAuthConstants.OUT_OF_BAND)).build(),
                new Token(getValueOrDefault(token, ""),
                getValueOrDefault(secret, "")));
    }

    private static String getValueOrDefault(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public Client(String serverLocation, OAuthService service, Token token) {
        this.serverLocation = serverLocation;
        this.service = service;
        this.token = token;
    }

	public String authorizeUrl() {
		requestToken = service.getRequestToken();
		return service.getAuthorizationUrl(requestToken);
	}

    public Client loginWithAccessToken(String token, String secret) {
        return new Client(serverLocation, service, new Token(token, secret));
	}

    /**
     * Logins as the first UserVoice account owner of the subdomain.
     *
     * @return The client instance for making API calls as the owner
     * @throws APIError
     */
    @SuppressWarnings("serial")
    public Client loginAsOwner() throws APIError, JSONException {
        requestToken = service.getRequestToken();
        JSONObject token = post("/api/v1/users/login_as_owner", new HashMap<String, Object>() {
            {
                put("request_token", requestToken.getToken());
            }
        });
        if (token != null && !(token.getJSONObject("token")==null)) {
            return loginWithAccessToken(token.getJSONObject("token").getString("oauth_token"),
                    token.getJSONObject("token").getString("oauth_token_secret"));
        } else {
            throw new Unauthorized("Could not get Request Token");
        }
    }

    /**
     * Logins as the specified email address.
     *
     * @param email
     *            The email of the user to be logged in as
     * @return The client instance for making API calls as the owner
     * @throws APIError
     */
    @SuppressWarnings({ "serial" })
    public Client loginAs(final String email) throws APIError, JSONException {
        requestToken = service.getRequestToken();
        JSONObject token = post("/api/v1/users/login_as", new HashMap<String, Object>() {
            {
                put("request_token", requestToken.getToken());
                put("user", new HashMap<String, String>() {
                    {
                        put("email", email);
                    }
                });
            }
        });
        if (token != null && !(token.getJSONObject("token")==null)) {
            return loginWithAccessToken(token.getJSONObject("token").getString("oauth_token"),
                    token.getJSONObject("token").getString("oauth_token_secret"));
        } else {
            throw new Unauthorized("Could not get Request Token");
        }
    }

    public JSONObject request(Verb method, String path, Map<String, Object> params)
        throws APIError, JSONException {
        OAuthRequest request = new OAuthRequest(method, serverLocation + path);
        request.addHeader("Content-Type", "application/json");
        request.addHeader("API-Client", "uservoice-java-${project.version}");
        request.addHeader("Accept", "application/json");
        if (params != null) {
            request.addPayload(new JSONObject(params).toString());
        }

        service.signRequest(token, request);
        Response response = request.send();

        JSONObject result = new JSONObject(response.getBody());
        if (result != null && !(result.getJSONObject("errors")==null)) {
            String errorType = result.getJSONObject("errors").getString("type");
            if ("unauthorized".equals(errorType)) {
                throw new Unauthorized(result.getJSONObject("errors").getString("message"));
            } else if ("record_not_found".equals(errorType)) {
                throw new NotFound(result.getJSONObject("errors").getString("message"));
            } else if ("application_error".equals(errorType)) {
                throw new ApplicationError(result.getJSONObject("errors").getString("message"));
            } else {
                throw new APIError(result.getJSONObject("errors").getString("message"));
            }
        }

        return result;
	}

    /**
     * Retrieve a client instance for making calls as the user who gave
     * permission in UserVoice site.
     *
     * @param verifier
     *            the verifier that was passed as a GET paramter or received in
     *            Out-Of-Band fashion
     * @return The client for making calls as the authorized and verified user
     */
    public Client loginWithVerifier(String verifier) {
        Token token = service.getAccessToken(requestToken, new Verifier(verifier));
        return loginWithAccessToken(token.getToken(), token.getSecret());
    }

    /**
     * Make a GET API call.
     *
     * @param path
     *            The GET path. Include the GET parameters after ?
     * @return A JSON object.
     * @throws APIError
     *             If an error occurs.
     */
    public JSONObject get(String path) throws APIError, JSONException {
        return request(Verb.GET, path, null);
    }

    /**
     * Make a DELETE API call.
     *
     * @param path
     *            The GET path. Include the GET parameters after ?
     * @return A JSON object.
     * @throws APIError
     *             If an error occurs.
     */
    public JSONObject delete(String path) throws APIError, JSONException {
        return request(Verb.DELETE, path, null);
    }

    /**
     * Make a GET POST call.
     *
     * @param path
     *            The GET path. Include the GET parameters after ?
     * @param params
     *            The parameters to be passed in the body of the call as JSON
     * @return A JSON object.
     * @throws APIError
     *             If an error occurs.
     */
    public JSONObject post(String path, Map<String, Object> params) throws APIError, JSONException {
        return request(Verb.POST, path, params);
    }

    /**
     * Make a PUT API call.
     *
     * @param path
     *            The GET path. Include the GET parameters after ?
     * @param params
     *            The parameters to be passed in the body of the call as JSON
     * @return A JSON object.
     * @throws APIError
     *             If an error occurs.
     */
    public JSONObject put(String path, Map<String, Object> params) throws APIError, JSONException {
        return request(Verb.PUT, path, params);
    }

    public Collection getCollection(String path, Integer limit) {
        return new Collection(this, path, limit);
    }

    public Collection getCollection(String path) {
        return getCollection(path, null);
    }

}