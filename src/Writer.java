
public class Writer {
	public Writer() {

	}

	public int run(String add, String coreid, int cycle) {
		// run write operation, and return the number of finishing cycle
		// if the operation is miss or hit
		Processor pro = Simulator.processorsTable.get(coreid);
		String homeid = Integer.parseInt(add.substring(19 - Simulator.p + 1, 20), 2) + "";
		Processor homeProcessor = Simulator.processorsTable.get(homeid);
		boolean hit = Util.hitOrMiss(add, pro, Simulator.n1, Simulator.a1, Simulator.b);

		if (hit) {
			int blockStatus = Util.getBlockStatus(coreid, add);
			Util.addCount(coreid, 1, true);
			if (blockStatus == Directory.MODIFIED_STATE) {
				// write hit, exclusive
				
				return hitExclusive(coreid, add, cycle);
			} else {
				// write hit, shared
				return share(coreid, homeid, add, cycle, hit);
			}
		} else {
			// System.out.println("***********");
			// System.out.println(add);
			// Set<String> keys =
			// homeProcessor.l2.directory.blocktable.keySet();
			// for(String key: keys){
			// System.out.println(key);
			// }
			// System.out.println("************");
			int start = cycle;
			Util.addCount(coreid, 1, false);
			int end = 0;
			if (homeProcessor.l2.directory.blocktable.containsKey(add)) {
				// write miss, l2 hit
				Util.addCount(homeid, 2, true);
				if (homeProcessor.l2.directory.blocktable.get(add).state == Directory.MODIFIED_STATE) {
					// write miss, l2 exclusive
					end = missExclusive(coreid, homeid, add, cycle);
				} else {
					// write miss, l2 shared
					end = share(coreid, homeid, add, cycle, hit);
				}
			} else {
				// write miss, l2 uncached
				Util.addCount(homeid, 2, false);
				end = uncached(coreid, homeid, add, cycle);
			}
			Util.addPenalty(coreid, end - start);
			return end;
		}
	}

	public int hitExclusive(String localid, String address, int cycle) {
		Util.updateLRU(address, localid, "l1", cycle);
		String str = localid + ": L1 write hit, write block.";
		Util.addOutput(cycle, str);
		return cycle;
	}

	public int uncached(String localid, String homeid, String address, int cycle) {
		int local2home = Util.getManhattanDistance(localid, homeid, Simulator.p);
		int home2controller = Util.getManhattanDistance(homeid, "0", Simulator.p);
		Processor processor = Simulator.processorsTable.get(homeid);

		// 1. L sends request to H
		String str = localid + ": L1 write miss, sends request to H:" + homeid + ". This is a short message.";
		Simulator.shortCount++;
		Util.addOutput(cycle, str);
		cycle = cycle + local2home * Simulator.C;
		if (Simulator.dSwitch){
			cycle = cycle + Simulator.d;
		}
		
		// 2. H sends request to 0
		str = homeid + ": gets request from L:" + localid
				+ ", L2 write miss, sends request to Memory Controller:0. This is a short message.";
		Simulator.shortCount++;
		Util.addOutput(cycle, str);
		cycle = cycle + home2controller * Simulator.C;
		if (!Simulator.dSwitch){
			cycle = cycle + Simulator.d;
		}
		
		// 3. 0 get data from mem
		// return to H
		str = 0 + ": gets request from H:" + homeid + ", starts to fetch data from memory.";
		Util.addOutput(cycle, str);
		cycle = cycle + Simulator.d1;

		str = 0 + ": gets request from H:" + homeid + ", gets block from memory, sends blocks to H:" + homeid
				+ ". This is a long message.";
		Simulator.longCount++;
		Util.addOutput(cycle, str);
		cycle = cycle + home2controller * Simulator.C;

		// 4. H get data, return to L
		// set state of block to "exclusive" in dir
		// store to l2
		// add owner L
		str = homeid
				+ ": gets block from Memory Controller:0, store to L2, set state of block to exclusive, set owner to L:"
				+ localid + ", sends blocks to L:" + localid + ". This is a long message.";
		Simulator.longCount++;
		Util.addOutput(cycle, str);
		cycle = cycle + Util.storeBlockToCache(address, "l2", homeid, cycle);
		processor.l2.directory.blocktable.get(address).state = Directory.MODIFIED_STATE;
		processor.l2.directory.blocktable.get(address).owner = localid;
		cycle = cycle + local2home * Simulator.C;

		// L get data
		// store to l1
		// set state of block to "exclusive"
		str = localid + ": gets block from H:" + homeid + ", store to L1, set state of block to exclusive, write block";
		Util.addOutput(cycle, str);
		cycle = cycle + Util.storeBlockToCache(address, "l1", localid, cycle);
		Util.setBlockStatus(localid, address, Directory.MODIFIED_STATE);

		return cycle;
	}

	public int missExclusive(String localid, String homeid, String address, int cycle) {
		Processor processor = Simulator.processorsTable.get(homeid);

		// 1. L sends request to H
		String str = localid + ": L1 write miss, sends request to H:" + homeid + ". This is a short message.";
		Simulator.shortCount++;
		Util.addOutput(cycle, str);
		int manhattanDistance = Util.getManhattanDistance(localid, homeid, Simulator.p);
		cycle = manhattanDistance * Simulator.C + cycle;
		if (Simulator.dSwitch){
			cycle = cycle + Simulator.d;
		}

		// 2. H return owner to L
		str = homeid + ": gets request from L:" + localid + ", L2 write hit(exclusive), sends owner to L:" + localid
				+ ". This is a short message.";
		Simulator.shortCount++;
		Util.addOutput(cycle, str);
		cycle = manhattanDistance * Simulator.C + cycle;

		// 3. L get R, sends request to R
		String remoteid = processor.l2.directory.blocktable.get(address).owner;
		str = localid + ": gets owner from H:" + homeid + ", sends request to R:" + remoteid
				+ ". This is a short message.";
		Simulator.shortCount++;
		Util.addOutput(cycle, str);
		manhattanDistance = Util.getManhattanDistance(localid, remoteid, Simulator.p);
		cycle = manhattanDistance * Simulator.C + cycle;

		// 4. R sends block to L and H
		// set state of block to "invalid"
		str = remoteid + ": gets request from L:" + localid + ", set state of block to invalid, sends block to L:"
				+ localid + ". This is a long message.";
		Simulator.longCount++;
		Util.addOutput(cycle, str);
		if (!homeid.equals(remoteid)) {
			str = remoteid + ": gets request from L:" + localid + ", set state of block to invalid, sends inform to H:"
					+ homeid + ". This is a short message.";
			Simulator.shortCount++;
			Util.addOutput(cycle, str);
		}
		Util.setBlockStatus(remoteid, address, Directory.INVALID_STATE);
		int cycleByL = 0;
		int cycleByH = 0;
		cycleByL = Util.getManhattanDistance(localid, remoteid, Simulator.p) * Simulator.C;
		cycleByH = Util.getManhattanDistance(homeid, remoteid, Simulator.p) * Simulator.C;

		// L get block
		// store to L1
		// set state of Block to "exclusive"
		str = localid + ": gets block from R:" + remoteid
				+ ", store to L1, set state of block to exclusive, write block.";
		Util.addOutput(cycleByL, str);
		cycleByL = cycleByL + Util.storeBlockToCache(address, "l1", localid, cycleByL);
		Util.setBlockStatus(localid, address, Directory.MODIFIED_STATE);

		// L get data perform write
		Util.updateLRU(address, localid, "l1", cycle + cycleByL);

		// H change to "exclusive"
		// store to L2
		// set owner to L
		if (!homeid.equals(remoteid)) {
			str = homeid + ": gets block from R:" + remoteid + ", set state of block to exclusive, sets owner to L:"
					+ localid;
			Util.addOutput(cycleByH, str);
		} else {
			str = homeid + ": set state of block to exclusive, sets owner to L:" + localid;
			Util.addOutput(cycleByH, str);
		}

		processor.l2.directory.blocktable.get(address).state = Directory.MODIFIED_STATE;
		Util.updateLRU(address, homeid, "l2", cycle + cycleByH);
		processor.l2.directory.blocktable.get(address).owner = localid;

		cycle = cycle + Math.max(cycleByL, cycleByH);
		return cycle;
	}

	public int share(String localid, String homeid, String address, int cycle, boolean hit) {
		Processor Processor = Simulator.processorsTable.get(homeid);
		int manhattanDistance = Util.getManhattanDistance(localid, homeid, Simulator.p);

		String str = "";
		// 1. L sends request to H
		if (hit) {
			str = localid + ": L1 write hit(shared), sends request to H:" + homeid + ". This is a short message.";
			Simulator.shortCount++;
			Util.addOutput(cycle, str);
		} else {
			str = localid + ": L1 write miss, sends request to H:" + homeid + ". This is a short message.";
			Simulator.shortCount++;
			Util.addOutput(cycle, str);
		}
		cycle = cycle + manhattanDistance * Simulator.C;
		if (Simulator.dSwitch){
			cycle = cycle + Simulator.d;
		}

		// 2. H return sharers list to L.
		// set block state to "exclusive"
		// change owner to L
		Util.updateLRU(address, homeid, "l2", cycle);
		Processor.l2.directory.blocktable.get(address).owner = localid;
		Processor.l2.directory.blocktable.get(address).state = Directory.MODIFIED_STATE;
		if (hit) {
			str = homeid + ": gets request from L:" + localid
					+ ", L2 write hit(shared), set state of block to exclusive, sends sharer list to L:" + localid
					+ ". This is a short message.";
			Simulator.shortCount++;
			Util.addOutput(cycle, str);
			// return a short message
		} else {
			str = homeid + ": gets request from L:" + localid
					+ ", L2 write hit(shared), set state of block to exclusive, sends sharer list and block to L:"
					+ localid + ". This is a long message.";
			Simulator.longCount++;
			Util.addOutput(cycle, str);
			if (!Simulator.dSwitch){
				cycle = cycle + Simulator.d;
			}
			// return a long message
		}
		cycle = cycle + manhattanDistance * Simulator.C;
		

		boolean hit1 = hit && Processor.l2.directory.blocktable.get(address).sharers.size() == 1;
		boolean miss0 = !hit && Processor.l2.directory.blocktable.get(address).sharers.size() == 0;

		if (hit1 || miss0) {
			// 3. there is no other sharer, perform write
			if (hit) {
				str = localid + ": gets sharer list from H:" + homeid
						+ ", but there is no other sharer, set state of block to exclusive, write block.";
				Util.addOutput(cycle, str);
			} else {
				str = localid + ": gets sharer list and block from H:" + homeid
						+ ", but there is no other sharer, store block to L1, set state of block to exclusive, write block.";
				Util.addOutput(cycle, str);
			}

			if (!hit) {
				cycle = cycle + Util.storeBlockToCache(address, "l1", localid, cycle);
			}

			Util.updateLRU(address, localid, "l1", cycle);
		} else {
			// 3. L sends invalidating message to sharers
			// set state to exclusive
			if (hit) {
				str = localid + ": gets sharer list from H:" + homeid
						+ ", set state of block to exclusive, sends invalidating message to Rs. Those are short messages";
				Util.addOutput(cycle, str);
			} else {
				str = localid + ": gets sharer list and block from H:" + homeid
						+ ", store block to L1, set state of block to exclusive, sends invalidating message to Rs. Those are short messages";
				Util.addOutput(cycle, str);
			}
			// no need to add message count here, I will add then in the next
			// iteration

			if (!hit) {
				cycle = cycle + Util.storeBlockToCache(address, "l1", localid, cycle);
			}
			Util.setBlockStatus(localid, address, Directory.MODIFIED_STATE);
			int longestLatency = 0;
			for (int i = 0; i < Processor.l2.directory.blocktable.get(address).sharers.size(); i++) {
				String rn = Processor.l2.directory.blocktable.get(address).sharers.get(i);
				if (!rn.equals(localid)) {
					int latency = Util.getManhattanDistance(localid, rn, Simulator.p);
					str = rn + ": gets invalidating message from L:" + localid + ", set state of block to invalid, sends ack message to L:" + localid
							+ ". this is short messages";
					// Since I haven't add count in the last step, so I have add
					// here twice
					Simulator.shortCount++;
					Simulator.shortCount++;
					Util.addOutput(cycle + latency * Simulator.C, str);
					if (latency > longestLatency) {
						longestLatency = latency;
					}
				}

			}
			cycle = cycle + longestLatency * Simulator.C;

			// 4. R sets block state to "invalid"
			// send ack to L
			for (int i = 0; i < Processor.l2.directory.blocktable.get(address).sharers.size(); i++) {
				String rn = Processor.l2.directory.blocktable.get(address).sharers.get(i);
				if (!rn.equals(localid)) {
					// set remote blocks' state to invalid
					Util.setBlockStatus(rn, address, Directory.INVALID_STATE);
				}
			}
			cycle = cycle + longestLatency * Simulator.C;

			// L get ack, perform write
			str = localid + ": gets all ack from Rs, write block.";
			Util.addOutput(cycle, str);
			Util.updateLRU(address, localid, "l1", cycle);
		}

		return cycle;
	}

}
