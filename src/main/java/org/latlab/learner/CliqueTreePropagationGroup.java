package org.latlab.learner;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.latlab.model.BayesNet;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagation;

/**
 * Used to hold a group of the clique tree propagation on the same model.
 * 
 * @author kmpoon
 * 
 */
public class CliqueTreePropagationGroup {
	private final BlockingQueue<CliqueTreePropagation> queue;
	public final BayesNet model;
	public final int capacity;

	public static CliqueTreePropagationGroup constructFromTemplate(
			CliqueTreePropagation template, BayesNet model, int capacity) {
		CliqueTreePropagationGroup group =
				new CliqueTreePropagationGroup(model, capacity);

		while (group.queue.size() < capacity) {
			CliqueTreePropagation ctp = template.clone();
			ctp.setBayesNet(model);
			group.queue.add(ctp);
		}

		return group;
	}

	public static CliqueTreePropagationGroup constructFromModel(BayesNet model,
			int capacity) {
		return new CliqueTreePropagationGroup(construct(model), capacity);
	}

	private CliqueTreePropagationGroup(BayesNet model, int capacity) {
		this.capacity = capacity;
		this.model = model;
		queue = new ArrayBlockingQueue<CliqueTreePropagation>(capacity);
	}

	public CliqueTreePropagationGroup(CliqueTreePropagation ctp, int capacity) {
		this(ctp.getBayesNet(), capacity);

		queue.add(ctp);

		while (queue.size() < capacity)
			queue.add(construct(model));
	}

	private static CliqueTreePropagation construct(BayesNet model) {
		if (model instanceof LTM)
			return new CliqueTreePropagation((LTM) model);
		else
			return new CliqueTreePropagation(model);
	}

	/**
	 * It constructs new clique tree propagation if necessary, otherwise reuses
	 * the ones in reserve.
	 * 
	 * @return
	 */
	public CliqueTreePropagation take() {
		try {
			return queue.take();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Puts back a clique tree propagation in reserve after use.
	 * 
	 * @param ctp
	 */
	public void put(CliqueTreePropagation ctp) {
		try {
			queue.put(ctp);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
