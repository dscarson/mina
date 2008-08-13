/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.mina.proxy;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.proxy.session.ProxyIoSession;

/**
 * ProxyLogicHandler.java - Interface implemented by classes containing proxy type specific logic.
 * 
 * @author James Furness <a href="mailto:james.furness@lehman.com">james.furness@lehman.com</a>
 * @author Edouard De Oliveira <a href="mailto:doe_wanted@yahoo.fr">doe_wanted@yahoo.fr</a>
 * @version $Id: $
 */
public interface ProxyLogicHandler {
    /**
     * Returns <code>true</code> if handshaking is complete and
     * data can be sent through the proxy.
     */
    public abstract boolean isHandshakeComplete();

    /**
     * Handle incoming data during the handshake process. Should consume only the
     * handshake data from the buffer, leaving any extra data in place.
     */
    public abstract void messageReceived(NextFilter nextFilter, IoBuffer buf)
            throws ProxyAuthException;

    /**
     * Called at each step of the handshake procedure.
     */
    public abstract void doHandshake(NextFilter nextFilter)
            throws ProxyAuthException;

    /**
     * Returns the {@link ProxyIoSession}.
     */
    public abstract ProxyIoSession getProxyIoSession();

    /**
     * Enqueue a message to be written once handshaking is complete.
     */
    public abstract void enqueueWriteRequest(final NextFilter nextFilter,
            final WriteRequest writeRequest);
}