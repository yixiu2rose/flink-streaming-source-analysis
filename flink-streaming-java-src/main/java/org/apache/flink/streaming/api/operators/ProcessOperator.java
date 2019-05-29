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

import org.apache.flink.annotation.Internal;
import org.apache.flink.streaming.api.TimerService;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.runtime.tasks.ProcessingTimeService;
import org.apache.flink.util.OutputTag;

import static org.apache.flink.util.Preconditions.checkState;

/**
 * A {@link org.apache.flink.streaming.api.operators.StreamOperator} for executing
 * {@link ProcessFunction ProcessFunctions}.
 */
/**
 * 一个执行 ProcessFunction 的操作符
 */
@Internal
public class ProcessOperator<IN, OUT>
		extends AbstractUdfStreamOperator<OUT, ProcessFunction<IN, OUT>>
		implements OneInputStreamOperator<IN, OUT> {

	private static final long serialVersionUID = 1L;

	private transient TimestampedCollector<OUT> collector;

	private transient ContextImpl context;

	/** We listen to this ourselves because we don't have an {@link InternalTimerService}. */
	private long currentWatermark = Long.MIN_VALUE;
	
	/**
	 * 构造函数，传入的是 ProcessFunction
	 */
	public ProcessOperator(ProcessFunction<IN, OUT> function) {
		super(function);

		chainingStrategy = ChainingStrategy.ALWAYS;
	}

	@Override
	public void open() throws Exception {
		super.open();
		// 保证 process emit 的流元素都是相同的时间戳
		collector = new TimestampedCollector<>(output);

		context = new ContextImpl(userFunction, getProcessingTimeService());
	}

	@Override
	public void processElement(StreamRecord<IN> element) throws Exception {
		collector.setTimestamp(element);
		context.element = element;  // context 记录当前处理的流元素
		userFunction.processElement(element.getValue(), context, collector);
		context.element = null;
	}

	/**
	 * 处理 watermark
	 */
	@Override
	public void processWatermark(Watermark mark) throws Exception {
		super.processWatermark(mark);
		this.currentWatermark = mark.getTimestamp();
	}

	/**
	 * ProcessFunction.Context 的实现类
	 */
	private class ContextImpl extends ProcessFunction<IN, OUT>.Context implements TimerService {
		private StreamRecord<IN> element;

		private final ProcessingTimeService processingTimeService;

		ContextImpl(ProcessFunction<IN, OUT> function, ProcessingTimeService processingTimeService) {
			function.super();
			this.processingTimeService = processingTimeService;
		}

		@Override
		public Long timestamp() {
			checkState(element != null);

			if (element.hasTimestamp()) {
				return element.getTimestamp();
			} else {
				return null;
			}
		}

		// 侧边输出
		@Override
		public <X> void output(OutputTag<X> outputTag, X value) {
			if (outputTag == null) {
				throw new IllegalArgumentException("OutputTag must not be null.");
			}
			output.collect(outputTag, new StreamRecord<>(value, element.getTimestamp()));
		}

		@Override
		public long currentProcessingTime() {
			return processingTimeService.getCurrentProcessingTime();
		}

		@Override
		public long currentWatermark() {
			return currentWatermark;
		}

		@Override
		public void registerProcessingTimeTimer(long time) {
			throw new UnsupportedOperationException(UNSUPPORTED_REGISTER_TIMER_MSG);
		}

		@Override
		public void registerEventTimeTimer(long time) {
			throw new UnsupportedOperationException(UNSUPPORTED_REGISTER_TIMER_MSG);
		}

		@Override
		public void deleteProcessingTimeTimer(long time) {
			throw new UnsupportedOperationException(UNSUPPORTED_DELETE_TIMER_MSG);
		}

		@Override
		public void deleteEventTimeTimer(long time) {
			throw new UnsupportedOperationException(UNSUPPORTED_DELETE_TIMER_MSG);
		}

		// 非 keyed stream TimerService 不支持任何定时器操作，返回 this
		// this 的所有定时器相关方法调用时候全部抛出异常
		@Override
		public TimerService timerService() {
			return this;
		}
	}
}