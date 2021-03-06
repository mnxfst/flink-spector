/*
 * Copyright 2015 Otto (GmbH & Co KG)
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

package org.flinkspector.core.runtime;

import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.flinkspector.core.trigger.VerifyFinishedTrigger;
import org.flinkspector.core.util.SerializeUtil;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Opens a 0MQ context and listens for output coming from a {@link OutputPublisher}.
 * This sink can run in parallel with multiple instances.
 * Feeds the {@link OutputVerifier} with the output and signals the result.
 *
 * @param <OUT> input type of the sink
 */
public class OutputSubscriber<OUT> implements Callable<OutputSubscriber.ResultState> {

	/**
	 * Verifier provided with output
	 */
	private final OutputVerifier<OUT> verifier;
	/**
	 * Number of parallel instances
	 */
	private int parallelism = -1;
	/**
	 * Set of parallel sink instances that have been started
	 */
	private final Set<Integer> participatingSinks = new HashSet<>();
	/**
	 * Set of finished parallel sink instances
	 */
	private final Set<Integer> closedSinks = new HashSet<>();
	/**
	 * Serializer to use for output
	 */
	TypeSerializer<OUT> typeSerializer = null;
	/**
	 * Number of records received
	 */
	private int numRecords = 0;
	/**
	 * Number of records reported
	 */
	private int expectedNumRecords = 0;
	/**
	 * Trigger
	 */
	private final VerifyFinishedTrigger<? super OUT> trigger;

	private final ZMQ.Socket subscriber;

	public OutputSubscriber(ZMQ.Socket subscriber,
	                        OutputVerifier<OUT> verifier,
	                        VerifyFinishedTrigger<? super OUT> trigger) {
		this.subscriber = subscriber;
		this.verifier = verifier;
		this.trigger = trigger;

	}

	/**
	 * Listens for output from the test sink.
	 *
	 * @return {@link OutputSubscriber.ResultState}
	 * @throws FlinkTestFailedException
	 */
	public ResultState call() throws FlinkTestFailedException {
		Action nextStep = Action.STOP;
		// receive output from sink until finished all sinks are finished
		try {
			nextStep = processMessage(subscriber.recv());
			while (nextStep == Action.CONTINUE) {
				nextStep = processMessage(subscriber.recv());
				//check if test is stopped
			}
		} catch (IOException e) {
			subscriber.close();
			throw new FlinkTestFailedException(e);
		} catch (ZMQException e) {
			//this means the socket was most likely closed forcefully by a timeout
		}
		// close the connection
		subscriber.close();
		try {
			verifier.finish();
		} catch (Throwable e) {
			throw new FlinkTestFailedException(e);
		}
		// determine the final state of the operation
		if (nextStep == Action.FINISH) {
			return ResultState.SUCCESS;
		} else if (nextStep == Action.STOP) {
			return ResultState.TRIGGERED;
		} else {
			return ResultState.FAILURE;
		}
	}

	/**
	 * Signals the final state of the {@link OutputSubscriber}
	 * SUCCESS if the verification process has been finished.
	 * TRIGGERED if a trigger stopped the verification.
	 * FAILURE if the verification protocol was interrupted.
	 */
	public enum ResultState {
		TRIGGERED, SUCCESS, FAILURE
	}

	/**
	 * Signals the next step after calling <pre>processMessage()</pre>.
	 * CONTINUE if further messages are expected.
	 * STOP if the a trigger has fired.
	 * FINISH if all messages have been received.
	 */
	private enum Action {
		CONTINUE, STOP, FINISH
	}

	/**
	 * Receives a byte encoded message.
	 * Determines the type of message, processes it
	 * and returns the next step.
	 *
	 * @param bytes byte representation of the msg.
	 * @return {@link Action} the next step.
	 * @throws IOException              if deserialization failed.
	 * @throws FlinkTestFailedException if the validation fails.
	 */
	private Action processMessage(byte[] bytes)
			throws IOException, FlinkTestFailedException {

		MessageType type = MessageType.getMessageType(bytes);
		String msg;
		byte[] out;

		switch (type) {
			case OPEN:
				//Received a open message one of the sink instances
				//--> Memorize the index and the parallelism.
				if (participatingSinks.isEmpty()) {
					verifier.init();
				}
				msg = new String(bytes, "UTF-8");
				String[] values = msg.split(" ");
				participatingSinks.add(Integer.parseInt(values[1]));
				parallelism = Integer.parseInt(values[2]);
				if (typeSerializer == null) {
					out = type.getPayload(bytes);
					typeSerializer = SerializeUtil.deserialize(out);
				}

				break;
			case REC:
				//Received a record message from the sink.
				//--> call the verifier and the finishing trigger.
				out = type.getPayload(bytes);
				OUT elem = SerializeUtil.deserialize(out, typeSerializer);
				numRecords++;

				try {
					verifier.receive(elem);
				} catch (Exception e) {
					throw new FlinkTestFailedException(e);
				}

				if (trigger.onRecord(elem) ||
						trigger.onRecordCount(numRecords)) {
					return Action.STOP;
				}
				break;
			case CLOSE:
				//Received a close message
				//--> register the index of the closed sink instance.
				msg = new String(bytes, "UTF-8");
				int sinkIndex = Integer.parseInt(msg.split(" ")[1]);
				int countRecords = Integer.parseInt(msg.split(" ")[2]);
				expectedNumRecords += countRecords;
				closedSinks.add(sinkIndex);
				break;
		}
		//check if all sink instances have been closed.
		if (closedSinks.size() == parallelism &&
				numRecords == expectedNumRecords) {
			if (participatingSinks.size() < parallelism) {
				throw new IOException("not all parallel sinks have been initialized");
			}
			//finish the listening process
			return Action.FINISH;
		}
		return Action.CONTINUE;
	}
}


