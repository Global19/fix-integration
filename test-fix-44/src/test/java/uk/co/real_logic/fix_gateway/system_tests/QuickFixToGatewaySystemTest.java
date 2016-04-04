/*
 * Copyright 2015 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.system_tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import quickfix.ConfigError;
import quickfix.SocketInitiator;
import io.aeron.driver.MediaDriver;
import uk.co.real_logic.fix_gateway.engine.FixEngine;
import uk.co.real_logic.fix_gateway.library.FixLibrary;
import uk.co.real_logic.fix_gateway.session.Session;

import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.agrona.CloseHelper.quietClose;
import static uk.co.real_logic.fix_gateway.TestFixtures.launchMediaDriver;
import static uk.co.real_logic.fix_gateway.TestFixtures.unusedPort;
import static uk.co.real_logic.fix_gateway.messages.SessionState.ACTIVE;
import static uk.co.real_logic.fix_gateway.system_tests.QuickFixUtil.assertQuickFixDisconnected;
import static uk.co.real_logic.fix_gateway.system_tests.QuickFixUtil.assertQuickFixReceivedMessage;
import static uk.co.real_logic.fix_gateway.system_tests.SystemTestUtil.*;

public class QuickFixToGatewaySystemTest
{

    private MediaDriver mediaDriver;
    private FixEngine acceptingEngine;
    private FixLibrary acceptingLibrary;
    private Session acceptedSession;

    private FakeOtfAcceptor acceptingOtfAcceptor = new FakeOtfAcceptor();
    private FakeSessionHandler acceptingSessionHandler = new FakeSessionHandler(acceptingOtfAcceptor);

    private SocketInitiator socketInitiator;
    private FakeQuickFixApplication initiator = new FakeQuickFixApplication();

    @Before
    public void launch() throws ConfigError
    {
        final int port = unusedPort();
        mediaDriver = launchMediaDriver();
        acceptingEngine = launchAcceptingEngine(port, ACCEPTOR_ID, INITIATOR_ID);
        acceptingLibrary = FixLibrary.connect(
            acceptingLibraryConfig(acceptingSessionHandler, ACCEPTOR_ID, INITIATOR_ID, "acceptingLibrary"));
        socketInitiator = QuickFixUtil.launchQuickFixInitiator(port, initiator);
        awaitQuickFixLogon();
        acceptedSession = acquireSession(acceptingSessionHandler, acceptingLibrary);
    }

    @Test
    public void sessionHasBeenInitiated()
    {
        assertThat(initiator.logons(), containsAcceptor());

        assertTrue("Session has failed to connect", acceptedSession.isConnected());
        assertTrue("Session has failed to logon", acceptedSession.state() == ACTIVE);
    }

    @Test
    public void messagesCanBeSentFromAcceptorToInitiator()
    {
        sendTestRequest(acceptedSession);

        assertQuickFixReceivedMessage(initiator);
    }

    @Test
    public void acceptorSessionCanBeDisconnected()
    {
        acceptedSession.startLogout();

        assertQuickFixDisconnected(initiator, containsAcceptor());
    }

    @After
    public void close() throws Exception
    {
        if (socketInitiator != null)
        {
            socketInitiator.stop();
        }

        quietClose(acceptingLibrary);
        quietClose(acceptingEngine);
        quietClose(mediaDriver);
    }

    private void awaitQuickFixLogon()
    {
        while (!socketInitiator.isLoggedOn())
        {
            acceptingLibrary.poll(1);
            LockSupport.parkNanos(1000);
        }
    }

}
