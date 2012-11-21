/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.mina.transport.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.mina.api.IoSession;
import org.apache.mina.api.RuntimeIoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class holds a Selector and handle all the incoming events for the sessions registered on this selector.ALl the
 * events will be processed by some dedicated thread, taken from a pool. It will loop forever, untill the instance is
 * stopped.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class NioSelectorLoop implements SelectorLoop {
    /** The logger for this class */
    private final Logger logger;

    /** the selector managed by this class */
    private Selector selector;

    /** the worker thread in charge of processing the events */
    private final SelectorWorker worker;

    /** Read buffer for all the incoming bytes (default to 64Kb) */
    private final ByteBuffer readBuffer = ByteBuffer.allocate(64 * 1024);

    /** The queue containing the channels to register on the selector */
    private final Queue<Registration> registrationQueue = new ConcurrentLinkedQueue<Registration>();

    /**
     * Creates an instance of the SelectorLoop.
     * 
     * @param prefix
     * @param index
     */
    public NioSelectorLoop(final String prefix) {
        this(prefix, -1);
    }

    /**
     * Creates an instance of the SelectorLoop.
     * 
     * @param prefix
     * @param index
     */
    public NioSelectorLoop(final String prefix, final int index) {
        String name = NioSelectorLoop.class.getName() + ":" + prefix;
        String workerName = "SelectorWorker " + prefix;

        if (index >= 0) {
            name += "-" + index;
            workerName += "-" + index;
        }

        logger = LoggerFactory.getLogger(name);
        worker = new SelectorWorker(workerName);

        try {
            logger.debug("open a selector");
            selector = Selector.open();
        } catch (final IOException ioe) {
            logger.error("Impossible to open a new NIO selector, O/S is out of file descriptor ?");
            throw new RuntimeIoException(ioe);
        }
        logger.debug("starting worker thread");
        worker.start();

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void register(boolean accept, boolean read, boolean write, SelectorListener listener,
            SelectableChannel channel) {
        register(null, accept, read, write, listener, channel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void register(IoSession session, boolean accept, boolean read, boolean write, SelectorListener listener,
            SelectableChannel channel) {
        logger.debug("registering : {} for accept : {}, read : {}, write : {}", new Object[] { listener, accept, read,
                write });
        int ops = 0;

        if (accept) {
            ops |= SelectionKey.OP_ACCEPT;
        }

        if (read) {
            ops |= SelectionKey.OP_READ;
        }

        if (write) {
            ops |= SelectionKey.OP_WRITE;
        }

        // TODO : if it's the same selector/worker, we don't need to do that we could directly enqueue
        registrationQueue.add(new Registration(session, ops, channel, listener));

        // Now, wakeup the selector in order to let it update the selectionKey status
        selector.wakeup();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void modifyRegistration(final boolean accept, final boolean read, final boolean write,
            final SelectorListener listener, final SelectableChannel channel) {
        logger.debug("modifying registration : {} for accept : {}, read : {}, write : {}", new Object[] { listener,
                accept, read, write });

        final SelectionKey key = channel.keyFor(selector);
        if (key == null) {
            logger.error("Trying to modify the registration of a not registered channel");
            return;
        }

        int ops = 0;
        if (accept) {
            ops |= SelectionKey.OP_ACCEPT;
        }
        if (read) {
            ops |= SelectionKey.OP_READ;
        }
        if (write) {
            ops |= SelectionKey.OP_WRITE;
        }
        key.interestOps(ops);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unregister(final SelectorListener listener, final SelectableChannel channel) {
        logger.debug("unregistering : {}", listener);
        final SelectionKey key = channel.keyFor(selector);
        if (key == null) {
            logger.error("Trying to modify the registration of a not registered channel");
            return;
        }
        key.cancel();
        key.attach(null);
        logger.debug("unregistering : {} done !", listener);

    }

    /**
     * The worker processing incoming session creation, session destruction requests, session write and reads. It will
     * also bind new servers.
     */
    private class SelectorWorker extends Thread {

        public SelectorWorker(String name) {
            super(name);
            setDaemon(true);
        }

        @Override
        public void run() {

            for (;;) {
                try {
                    logger.debug("selecting...");
                    final int readyCount = selector.select();
                    logger.debug("... done selecting : {} events", readyCount);
                    final Iterator<SelectionKey> it = selector.selectedKeys().iterator();

                    while (it.hasNext()) {
                        final SelectionKey key = it.next();
                        final SelectorListener listener = (SelectorListener) key.attachment();
                        listener.ready(key.isAcceptable(), key.isReadable(), key.isReadable() ? readBuffer : null,
                                key.isWritable());
                        // if you don't remove the event of the set, the selector will present you this event again and
                        // again
                        it.remove();
                    }

                    // new registration
                    while (!registrationQueue.isEmpty()) {
                        final Registration reg = registrationQueue.poll();

                        try {
                            SelectionKey selectionKey = reg.channel.register(selector, reg.ops, reg.listener);

                            IoSession session = reg.getSession();

                            if (session != null) {
                                ((NioSession) session).setSelectionKey(selectionKey);
                            }
                        } catch (final ClosedChannelException ex) {
                            // dead session..
                            logger.error("socket is already dead", ex);
                        }
                    }
                } catch (final Exception e) {
                    logger.error("Unexpected exception : ", e);
                    e.printStackTrace();
                }
            }
        }
    }

    private class Registration {

        public Registration(IoSession session, int ops, SelectableChannel channel, SelectorListener listener) {
            this.ops = ops;
            this.channel = channel;
            this.listener = listener;
            this.session = session;
        }

        private final int ops;

        private final SelectableChannel channel;

        private final SelectorListener listener;

        private final IoSession session;

        public IoSession getSession() {
            return session;
        }
    }

    @Override
    public void wakeup() {
        selector.wakeup();
    }
}