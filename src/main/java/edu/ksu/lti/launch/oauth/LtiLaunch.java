package edu.ksu.lti.launch.oauth;

import edu.ksu.canvas.CanvasApiFactory;
import edu.ksu.canvas.interfaces.UserReader;
import edu.ksu.canvas.model.User;
import edu.ksu.canvas.oauth.OauthTokenRefresher;
import edu.ksu.canvas.oauth.RefreshableOauthToken;
import edu.ksu.lti.launch.exception.NoLtiSessionException;
import edu.ksu.lti.launch.exception.OauthTokenRequiredException;
import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.service.ConfigService;
import edu.ksu.lti.launch.service.LtiSessionService;
import edu.ksu.lti.launch.service.OauthTokenService;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/*
 * This class was extracted from a subset of functions from LtiLaunchController
 */
@Component
@Scope("session")
public class LtiLaunch {
    private static final Logger LOG = LogManager.getLogger(LtiLaunch.class);
    @Autowired
    private OauthTokenService oauthTokenService;
    @Autowired
    private LtiSessionService ltiSessionService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private CanvasApiFactory apiFactory;



    public edu.ksu.canvas.oauth.OauthToken ensureApiTokenPresent() throws OauthTokenRequiredException, NoLtiSessionException {
        LtiSession ltiSession = ltiSessionService.getLtiSession();
        if (ltiSession.getOauthToken() != null) {
            return ltiSession.getOauthToken();
        }
        if (ltiSession.getEid() != null) {
            String refreshToken = oauthTokenService.getRefreshToken(ltiSession.getEid());
            if (refreshToken != null) {
                String clientId = configService.getConfigValue("oauth_client_id");
                String clientSecret = configService.getConfigValue("oauth_client_secret");
                String canvasUrl = configService.getConfigValue("canvas_url");
                OauthTokenRefresher tokenRefresher = new OauthTokenRefresher(clientId, clientSecret, canvasUrl);
                ltiSession.setOauthToken(new RefreshableOauthToken(tokenRefresher, refreshToken));
                return ltiSession.getOauthToken();
            }
            throw new OauthTokenRequiredException();
        }
        // If the eid is null, we need to get a new session.
        throw new NoLtiSessionException();

    }

    /**
     * Perform a trivial Canvas operation to verify if the OAuth Token is valid.
     * This will typically be used when we want an integration to fail fast.
     * Some integrations might not notice that the OAuth token is invalid until
     * halfway into whatever it is that they do. This was initially the case
     * for the Scantron integration.
     *
     * @throws NoLtiSessionException       When there isn't a valid ltiExcpetion
     * @throws IOException                 when exception communicating with canvas
     */
    public void validateOAuthToken() throws NoLtiSessionException, IOException {
        LtiSession ltiSession = ltiSessionService.getLtiSession();
        UserReader userReader = apiFactory.getReader(UserReader.class, ltiSession.getOauthToken());
        //If call to API endpoint succeeds without an exception, then the OAuth token is valid
        Optional<User> user = userReader.showUserDetails("self");
        if(user.isPresent()) {
            LOG.debug("Validated OAuth token for canvas user: ", user.get().getId());
        }
    }



}
