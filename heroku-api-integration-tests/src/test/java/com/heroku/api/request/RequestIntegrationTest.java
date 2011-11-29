package com.heroku.api.request;

import com.heroku.api.HerokuAPI;
import com.heroku.api.request.addon.AddonInstall;
import com.heroku.api.request.addon.AddonList;
import com.heroku.api.request.addon.AppAddonsList;
import com.heroku.api.request.app.AppCreate;
import com.heroku.api.request.app.AppDestroy;
import com.heroku.api.request.app.AppInfo;
import com.heroku.api.request.app.AppList;
import com.heroku.api.request.config.ConfigAdd;
import com.heroku.api.request.config.ConfigList;
import com.heroku.api.request.config.ConfigRemove;
import com.heroku.api.request.log.Log;
import com.heroku.api.request.log.LogStream;
import com.heroku.api.request.log.LogStreamResponse;
import com.heroku.api.request.ps.ProcessList;
import com.heroku.api.request.ps.Restart;
import com.heroku.api.request.ps.Scale;
import com.heroku.api.request.response.JsonArrayResponse;
import com.heroku.api.request.response.JsonMapResponse;
import com.heroku.api.request.response.Unit;
import com.heroku.api.request.response.XmlArrayResponse;
import com.heroku.api.request.sharing.CollabList;
import com.heroku.api.request.sharing.SharingAdd;
import com.heroku.api.request.sharing.SharingRemove;
import com.heroku.api.request.sharing.SharingTransfer;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.heroku.api.Heroku.Stack.Cedar;
import static org.testng.Assert.*;


/**
 * TODO: Javadoc
 *
 * @author Naaman Newbold
 */
public class RequestIntegrationTest extends BaseRequestIntegrationTest {

    // test app gets transferred to this user until we have a second user in auth-test.properties
    private static final String DEMO_EMAIL = "jw+demo@heroku.com";

    @Test
    public void testCreateAppCommand() throws IOException {
        AppCreate cmd = new AppCreate("Cedar");
        Response response = connection.execute(cmd);

        assertNotNull(response.get("id"));
        assertEquals(response.get("stack").toString(), "cedar");
        deleteApp(response.get("name").toString());
    }

    @Test(dataProvider = "app")
    public void testLogCommand(JsonMapResponse app) throws IOException, InterruptedException {
        System.out.println("Sleeping to wait for logplex provisioning");
        Thread.sleep(10000);
        Log logs = new Log(app.get("name"));
        LogStreamResponse logsResponse = connection.execute(logs);
        assertLogIsReadable(logsResponse);
    }

    @Test(dataProvider = "app")
    public void testLogStreamCommand(JsonMapResponse app) throws IOException, InterruptedException {
        System.out.println("Sleeping to wait for logplex provisioning");
        Thread.sleep(10000);
        LogStream logs = new LogStream(app.get("name"));
        LogStreamResponse logsResponse = connection.execute(logs);
        assertLogIsReadable(logsResponse);
    }

    @Test(dataProvider = "app")
    public void testAppCommand(JsonMapResponse app) throws IOException {
        AppInfo cmd = new AppInfo(app.get("name"));
        Response response = connection.execute(cmd);
        assertEquals(response.get("name"), app.get("name"));
    }

    @Test(dataProvider = "app")
    public void testListAppsCommand(JsonMapResponse app) throws IOException {
        AppList cmd = new AppList();
        Response response = connection.execute(cmd);
        assertNotNull(response.get(app.get("name")));
    }

    // don't use the app dataprovider because it'll try to delete an already deleted app
    @Test
    public void testDestroyAppCommand() throws IOException {
        AppDestroy cmd = new AppDestroy(HerokuAPI.with(connection).newapp(Cedar).getAppName());
        connection.execute(cmd);
    }

    @Test(dataProvider = "app")
    public void testSharingAddCommand(JsonMapResponse app) throws IOException {
        SharingAdd cmd = new SharingAdd(app.get("name"), DEMO_EMAIL);
        connection.execute(cmd);
    }

    // if we do this then we will no longer be able to remove the app
    // we need two users in auth-test.properties so that we can transfer it to one and still control it,
    // rather than transferring it to a black hole
    @Test(dataProvider = "app")
    public void testSharingTransferCommand(JsonMapResponse app) throws IOException {
        Request<Unit> sharingAddReq = new SharingAdd(app.get("name"), DEMO_EMAIL);
        connection.execute(sharingAddReq);

        SharingTransfer sharingTransferCommand = new SharingTransfer(app.get("name"), DEMO_EMAIL);
        connection.execute(sharingTransferCommand);

    }

    @Test(dataProvider = "app")
    public void testSharingRemoveCommand(JsonMapResponse app) throws IOException {
        SharingAdd sharingAddCommand = new SharingAdd(app.get("name"), DEMO_EMAIL);
        connection.execute(sharingAddCommand);

        SharingRemove cmd = new SharingRemove(app.get("name"), DEMO_EMAIL);
        connection.execute(cmd);

    }

    @Test(dataProvider = "app")
    public void testConfigAddCommand(JsonMapResponse app) throws IOException {
        ConfigAdd cmd = new ConfigAdd(app.get("name"), "{\"FOO\":\"bar\", \"BAR\":\"foo\"}");
        connection.execute(cmd);
    }

    @Test(dataProvider = "app")
    public void testConfigCommand(JsonMapResponse app) {
        addConfig(app, "FOO", "BAR");
        Request<JsonMapResponse> req = new ConfigList(app.get("name"));
        JsonMapResponse response = connection.execute(req);
        assertNotNull(response.get("FOO"));
        assertEquals(response.get("FOO"), "BAR");
    }

    @Test(dataProvider = "app",
            expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "FOO is not present.")
    public void testConfigRemoveCommand(JsonMapResponse app) {
        addConfig(app, "FOO", "BAR", "JOHN", "DOE");
        Request<JsonMapResponse> removeRequest = new ConfigRemove(app.get("name"), "FOO");
        connection.execute(removeRequest);

        Request<JsonMapResponse> listRequest = new ConfigList(app.get("name"));
        JsonMapResponse response = connection.execute(listRequest);

        assertNotNull(response.get("JOHN"), "Config var 'JOHN' should still exist, but it's not there.");
        response.get("FOO");
    }

    @Test(dataProvider = "app")
    public void testProcessCommand(JsonMapResponse app) {
        Request<JsonArrayResponse> req = new ProcessList(app.get("name"));
        JsonArrayResponse response = connection.execute(req);
        assertNotNull(response.getData(), "Expected a non-null response for a new app, but the data was null.");
        assertEquals(response.getData().size(), 1);
    }

    @Test(dataProvider = "app")
    public void testScaleCommand(JsonMapResponse app) {
        Request<Unit> req = new Scale(app.get("name"), "web", 1);
        connection.execute(req);
    }

    @Test(dataProvider = "app")
    public void testRestartCommand(JsonMapResponse app) {
        Request<Unit> req = new Restart(app.get("name"));
        connection.execute(req);
    }

    @Test
    public void testListAddons() {
        Request<JsonArrayResponse> req = new AddonList();
        JsonArrayResponse response = connection.execute(req);
        assertNotNull(response, "Expected a response from listing addons, but the result is null.");
    }

    @Test(dataProvider = "app")
    public void testListAppAddons(JsonMapResponse app) {
        Request<JsonArrayResponse> req = new AppAddonsList(app.get("name"));
        JsonArrayResponse response = connection.execute(req);
        assertNotNull(response);
        assertTrue(response.getData().size() > 0, "Expected at least one addon to be present.");
        assertNotNull(response.get("releases:basic"));
    }

    @Test(dataProvider = "app")
    public void testAddAddonToApp(JsonMapResponse app) {
        Request<JsonMapResponse> req = new AddonInstall(app.get("name"), "shared-database:5mb");
        JsonMapResponse response = connection.execute(req);
        assertEquals(response.get("status"), "Installed");
    }

    @Test(dataProvider = "app")
    public void testCollaboratorList(JsonMapResponse app) {
        Request<XmlArrayResponse> req = new CollabList(app.get("name"));
        XmlArrayResponse xmlArrayResponse = connection.execute(req);
        assertEquals(xmlArrayResponse.getData().size(), 1);
        assertNotNull(xmlArrayResponse.getData().get(0).get("email"));
    }




}