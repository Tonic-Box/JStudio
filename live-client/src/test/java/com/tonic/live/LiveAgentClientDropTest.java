package com.tonic.live;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.live.protocol.LiveEvent;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("a dropped agent connection fails requests immediately")
class LiveAgentClientDropTest
{

    @Test
    @DisplayName("a request after the agent stops sending throws instead of waiting out the timeout")
    void requestAfterDropFailsFast() throws Exception
    {
        CountDownLatch hangUp = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0))
        {
            Thread agent = new Thread(() ->
            {
                try (Socket socket = server.accept())
                {
                    hangUp.await();
                    socket.shutdownOutput();
                    finished.await();
                }
                catch (IOException | InterruptedException ignored)
                {
                }
            });
            agent.start();

            try (LiveAgentClient client = LiveAgentClient.connect("127.0.0.1", server.getLocalPort(), 5000))
            {
                CountDownLatch dropped = new CountDownLatch(1);
                client.addEventListener(event ->
                {
                    if (event.getKind() == LiveEvent.Kind.VM_DEATH)
                    {
                        dropped.countDown();
                    }
                });
                hangUp.countDown();
                assertTrue(dropped.await(10, TimeUnit.SECONDS));

                assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThrows(IOException.class, client::hello));
            }
            finally
            {
                finished.countDown();
                agent.join();
            }
        }
    }
}
