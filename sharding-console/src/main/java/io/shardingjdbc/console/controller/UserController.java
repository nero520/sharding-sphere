package io.shardingjdbc.console.controller;

import com.google.common.base.Optional;
import io.shardingjdbc.console.domain.*;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User controller.
 * 
 * @author panjuan
 */
@RestController
@RequestMapping("/user")
public class UserController {
    
    /**
     * Handle https for user login.
     * 
     * @param userSession user info
     * @param userUUID user uuid
     * @param response response
     * @return response object
     */
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public WorkbenchResponse login(@RequestBody final UserSession userSession, final @CookieValue(value = "userUUID", required = false, defaultValue = "") String userUUID,
                                   final HttpServletResponse response) throws UserException {
        if (!"".equals(userUUID)) {
            Optional<UserSession> userSessionOptional = UserSessionRegistry.getInstance().findSession(userUUID);
            if (userSessionOptional.isPresent()) {
                return new WorkbenchResponse("Logged in.");
            } else {
                removeSession(userUUID, response);
                throw new UserException("Please login first.");
            }
        }
        WindowSession windowSession;
        try {
            windowSession = createWindow(userSession);
        } catch (UserException ex) {
            throw ex;
        }
        setSession(userSession, windowSession, response);
        Map<String, String> result = new HashMap<>();
        result.put("windowID", windowSession.getId());
        return new WorkbenchResponse("Login succeeded", result);
    }
    
    private WindowSession createWindow(UserSession userSession) throws UserException {
        try {
            Connection connection = DBConnector.getConnection(userSession.getUserName(), userSession.getPassWord(), userSession.getTargetURL(), userSession.getDriver());
            return new WindowSession(connection);
        } catch (final ClassNotFoundException | SQLException ex) {
            throw new UserException("Login failed.");
        }
        
    }
    private void setSession(final UserSession userSession, final WindowSession windowSession,
                            final HttpServletResponse response) {
        userSession.addWindowID(windowSession.getId());
        UserSessionRegistry.getInstance().addSession(userSession.getId(), userSession);
        WindowSessionRegistry.getInstance().addSession(windowSession.getId(), windowSession.getConnection());
        
        Cookie cookie = new Cookie("userUUID", userSession.getId());
        cookie.setMaxAge(120 * 60);
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    /**
     * Handle http for user exit.
     * 
     * @param userUUID user uuid
     * @param response response
     * @return response object
     */
    @RequestMapping(value = "/exit", method = RequestMethod.POST)
    public WorkbenchResponse exit(final @CookieValue(value = "userUUID", required = false, defaultValue = "") String userUUID, final HttpServletResponse response) {
        if (!"".equals(userUUID)) {
            removeSession(userUUID, response);
        }
        return new WorkbenchResponse("Logout succeeded");
    }

    private void removeSession(final String userUUID, final HttpServletResponse response) {
        Optional<UserSession> userSessionOptional = UserSessionRegistry.getInstance().findSession(userUUID);
        if(userSessionOptional.isPresent()) {
            List<String> windowIDList = userSessionOptional.get().getWindowIDList();
            for(String windowID : windowIDList){
                WindowSessionRegistry.getInstance().removeSession(windowID);
            }
        }
        UserSessionRegistry.getInstance().removeSession(userUUID);
        Cookie cookie = new Cookie("userUUID", null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);
    }
    
    @RequestMapping(value = "/addWindow", method = RequestMethod.POST)
    public WorkbenchResponse addWindow(final @CookieValue(value = "userUUID",
        required = false, defaultValue = "") String userUUID) throws UserException {
        Optional<UserSession> userSessionOptional  = UserSessionRegistry.getInstance().findSession(userUUID);
        if(!userSessionOptional.isPresent()) {
            throw new UserException("Please login first.");
        }
        
        UserSession userSession = userSessionOptional.get();
        WindowSession windowSession = createWindow(userSession);
        setWindow(userSession, windowSession);
        
        
        Map<String, String> result = new HashMap<>();
        result.put("windowID", windowSession.getId());
        return new WorkbenchResponse("Open new window OK.",result);
    }
    
    private void setWindow(final UserSession userSession, final WindowSession windowSession) {
        userSession.addWindowID(windowSession.getId());
        WindowSessionRegistry.getInstance().addSession(windowSession.getId(), windowSession.getConnection());
    }
    
    @RequestMapping(value = "/delWindow", method = RequestMethod.POST)
    public WorkbenchResponse delWindow(@RequestBody final Map<String, String> windowInfo, final @CookieValue(value = "userUUID",
        required = false, defaultValue = "") String userUUID) throws UserException {
        Optional<UserSession> userSessionOptional  = UserSessionRegistry.getInstance().findSession(userUUID);
        if(!userSessionOptional.isPresent()) {
            throw new UserException("Please login first.");
        }
        
        UserSession userSession = userSessionOptional.get();
        removeWindow(userSession, windowInfo);
        
        return new WorkbenchResponse("Close window OK.");
    }
    
    private void removeWindow(final UserSession userSession, final Map<String, String> windowInfo) {
        userSession.delWindowID(windowInfo.get("windowID"));
        WindowSessionRegistry.getInstance().removeSession(windowInfo.get("windowID"));
        }
}
