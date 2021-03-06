/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.api.operators;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.runtime.checkpoint.CheckpointOptions;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.runtime.state.CheckpointListener;
import org.apache.flink.runtime.state.CheckpointStreamFactory;
import org.apache.flink.streaming.api.graph.StreamConfig;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.runtime.tasks.StreamTask;
import org.apache.flink.util.Disposable;

import java.io.Serializable;

/**
 * Basic interface for stream operators. Implementers would implement one of
 * {@link org.apache.flink.streaming.api.operators.OneInputStreamOperator} or
 * {@link org.apache.flink.streaming.api.operators.TwoInputStreamOperator} to create operators
 * that process elements.
 *
 * <p>The class {@link org.apache.flink.streaming.api.operators.AbstractStreamOperator}
 * offers default implementation for the lifecycle and properties methods.
 *
 * <p>Methods of {@code StreamOperator} are guaranteed not to be called concurrently. Also, if using
 * the timer service, timer callbacks are also guaranteed not to be called concurrently with
 * methods on {@code StreamOperator}.
 *
 * @param <OUT> The output type of the operator
 */
/**
 * 流操作符，有两个子接口 OneInputStreamOperator/TwoInputStreamOperator
 * AbstractStreamOperator 抽象类定义了默认的操作符的生命周期以及属性方法
 * StreamOperator 的方法不能被并发调用！！！
 */
@PublicEvolving
public interface StreamOperator<OUT> extends CheckpointListener, KeyContext, Disposable, Serializable {

	// ------------------------------------------------------------------------
	//  life cycle
	// ------------------------------------------------------------------------

	/**
	 * Initializes the operator. Sets access to the context and the output.
	 */
	/**
	 * 初始化 operator，设置对上下文和输出的访问
	 */
	void setup(StreamTask<?, ?> containingTask, StreamConfig config, Output<StreamRecord<OUT>> output);

	/**
	 * This method is called immediately before any elements are processed, it should contain the
	 * operator's initialization logic.
	 *
	 * @throws java.lang.Exception An exception in this method causes the operator to fail.
	 */
	/**
	 * 本方法需要在第一个 element 被处理前调用，本方法内需要包括操作符的初始化逻辑
	 */
	void open() throws Exception;

	/**
	 * This method is called after all records have been added to the operators via the methods
	 * {@link org.apache.flink.streaming.api.operators.OneInputStreamOperator#processElement(StreamRecord)}, or
	 * {@link org.apache.flink.streaming.api.operators.TwoInputStreamOperator#processElement1(StreamRecord)} and
	 * {@link org.apache.flink.streaming.api.operators.TwoInputStreamOperator#processElement2(StreamRecord)}.
	 *
	 * <p>The method is expected to flush all remaining buffered data. Exceptions during this
	 * flushing of buffered should be propagated, in order to cause the operation to be recognized
	 * as failed, because the last data items are not processed properly.
	 *
	 * @throws java.lang.Exception An exception in this method causes the operator to fail.
	 */
	/**
	 * 在所有的 records 通过 processElement/processElement1/processElement2 三个方法被加入 operator 后，调用本方法
	 * 本方法预期会将剩余的缓存的数据刷入 network，本方法中抛出的异常需要被传播，因为最后的数据没有被正确的处理
	 */
	void close() throws Exception;

	/**
	 * This method is called at the very end of the operator's life, both in the case of a successful
	 * completion of the operation, and in the case of a failure and canceling.
	 *
	 * <p>This method is expected to make a thorough effort to release all resources
	 * that the operator has acquired.
	 */
	/**
	 * 无论 operator 执行成功或失败，在 operator 生命周期最后，调用本方法
	 * 同时释放 operator 申请的所有资源
	 */

	@Override
	void dispose() throws Exception;

	// ------------------------------------------------------------------------
	//  state snapshots
	// ------------------------------------------------------------------------
	// 状态快照
	/**
	 * This method is called when the operator should do a snapshot, before it emits its
	 * own checkpoint barrier.
	 *
	 * <p>This method is intended not for any actual state persistence, but only for emitting some
	 * data before emitting the checkpoint barrier. Operators that maintain some small transient state
	 * that is inefficient to checkpoint (especially when it would need to be checkpointed in a
	 * re-scalable way) but can simply be sent downstream before the checkpoint. An example are
	 * opportunistic pre-aggregation operators, which have small the pre-aggregation state that is
	 * frequently flushed downstream.
	 *
	 * <p><b>Important:</b> This method should not be used for any actual state snapshot logic, because
	 * it will inherently be within the synchronous part of the operator's checkpoint. If heavy work is done
	 * within this method, it will affect latency and downstream checkpoint alignments.
	 *
	 * @param checkpointId The ID of the checkpoint.
	 * @throws Exception Throwing an exception here causes the operator to fail and go into recovery.
	 */
	/**
	 * 在操作符发出检查点屏障之前，需要生成一个快照，调用这个方法
	 * 
	 * 此方法不适用于任何实际的状态持久性，而仅适用于在发出检查点屏障之前发出一些数据
	 * 操作符维持一些小的 transient 状态，这些状态对检查点来说是低效的
	 * （特别是当它需要以可重新扩展的方式进行检查点时），但可以简单地在检查点之前向下游发送
	 * 一个例子是机会预聚合运算符，它具有经常在下游刷新的预聚合状态
	 * 
	 * 此方法不应用于任何实际的状态快照逻辑，因为它本身就位于运算符检查点的同步部分内
	 * 如果在此方法中进行繁重的工作，则会影响延迟和下游检查点对齐
	 */
	void prepareSnapshotPreBarrier(long checkpointId) throws Exception;

	/**
	 * Called to draw a state snapshot from the operator.
	 *
	 * @return a runnable future to the state handle that points to the snapshotted state. For synchronous implementations,
	 * the runnable might already be finished.
	 *
	 * @throws Exception exception that happened during snapshotting.
	 */
	/**
	 * 生成快照
	 */
	OperatorSnapshotFutures snapshotState(
		long checkpointId,
		long timestamp,
		CheckpointOptions checkpointOptions,
		CheckpointStreamFactory storageLocation) throws Exception;

	/**
	 * Provides a context to initialize all state in the operator.
	 */
	/**
	 * 提供上下文以初始化运算符中的所有状态
	 */
	void initializeState() throws Exception;

	// ------------------------------------------------------------------------
	//  miscellaneous
	// ------------------------------------------------------------------------
	// input1 传递过来数据，从 KeySelector1 中获取 key，调用 setCurrentKey
	void setKeyContextElement1(StreamRecord<?> record) throws Exception;

	void setKeyContextElement2(StreamRecord<?> record) throws Exception;

	ChainingStrategy getChainingStrategy();

	void setChainingStrategy(ChainingStrategy strategy);

	MetricGroup getMetricGroup();

	OperatorID getOperatorID();
}
