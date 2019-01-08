/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.graph.runtime.jmx;

import java.util.ArrayList;
import java.util.List;

import org.jetel.graph.Node;
import org.jetel.graph.Phase;
import org.jetel.graph.Result;
import org.jetel.graph.TransformationGraphAnalyzer;
import org.jetel.graph.runtime.NodeTrackingProvider;
import org.jetel.graph.runtime.PhaseTrackingProvider;
import org.jetel.util.ClusterUtils;

/**
 * Simple DTO holding tracking information about a phase.
 * 
 * @author Filip Reichman
 *         (c) Javlin Consulting (www.javlinconsulting.cz)
 *
 * @created Jan 2, 2019
 */
public class PhaseTrackingImpl implements PhaseTracking {

	private static final long serialVersionUID = 929691539023786046L;
	
	protected int phaseNum;
	
	protected long startTime = -1;

	protected long endTime = -1;
    
	protected long memoryUtilization;
    
	protected Result result;
	
	protected String phaseLabel;

	private NodeTracking[] nodesDetails;
	
	public PhaseTrackingImpl(GraphTracking graphTracking) {
		this.nodesDetails = new NodeTracking[0];
	}
	
	public PhaseTrackingImpl(PhaseTrackingProvider phaseTracking) {
		this.startTime = phaseTracking.getStartTime();
		this.endTime = phaseTracking.getEndTime();
		this.memoryUtilization = phaseTracking.getMemoryUtilization();
		this.result = phaseTracking.getResult();
		this.phaseNum = phaseTracking.getPhaseNum();
		this.phaseLabel = phaseTracking.getPhaseLabel();
		
		this.nodesDetails = new NodeTracking[phaseTracking.getNodeTracking().length];
		int i = 0;
		for (NodeTrackingProvider nodeDetail : phaseTracking.getNodeTracking()) {
			this.nodesDetails[i++] = nodeDetail.createSnapshot(this);
		}
	}
	
	public PhaseTrackingImpl(Phase phase) {
		this.phaseNum = phase.getPhaseNum();
		this.result = Result.N_A;
		this.phaseLabel = phase.getLabel();
		
		List<NodeTrackingImpl> details = new ArrayList<>();
		for (Node node : TransformationGraphAnalyzer.nodesTopologicalSorting(new ArrayList<Node>(phase.getNodes().values()))) {
			if (!ClusterUtils.isRemoteEdgeComponent(node.getType())
					&& !ClusterUtils.isClusterRegather(node.getType())) {
				details.add(new NodeTrackingImpl(this, node));
			}
		}
		this.nodesDetails = details.toArray(new NodeTrackingImpl[details.size()]);
	}
	
	@Override
	public String getPhaseLabel() {
		return phaseLabel;
	}

	@Override
	public NodeTracking[] getNodeTracking() {
		return nodesDetails;
	}

	/* (non-Javadoc)
	 * @see org.jetel.graph.runtime.jmx.PhaseTracking#getNodeTracking(java.lang.String)
	 */
	@Override
	public NodeTracking getNodeTracking(String nodeID) {
		for (NodeTracking nodeDetail : nodesDetails) {
			if(nodeID.equals(nodeDetail.getNodeID())) {
				return nodeDetail;
			}
		}
		
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.jetel.graph.runtime.jmx.PhaseTracking#getPhaseNum()
	 */
	@Override
	public int getPhaseNum() {
		return phaseNum;
	}
	
	/* (non-Javadoc)
	 * @see org.jetel.graph.runtime.jmx.PhaseTracking#getStartTime()
	 */
	@Override
	public long getStartTime() {
		return startTime;
	}

	/* (non-Javadoc)
	 * @see org.jetel.graph.runtime.jmx.PhaseTracking#getEndTime()
	 */
	@Override
	public long getEndTime() {
		return endTime;
	}

	/* (non-Javadoc)
	 * @see org.jetel.graph.runtime.jmx.PhaseTracking#getMemoryUtilization()
	 */
	@Override
	public long getMemoryUtilization() {
		return memoryUtilization;
	}

	/* (non-Javadoc)
	 * @see org.jetel.graph.runtime.jmx.PhaseTracking#getResult()
	 */
	@Override
	public Result getResult() {
		return result;
	}
	
	/* (non-Javadoc)
	 * @see org.jetel.graph.runtime.jmx.PhaseTracking#getExecutionTime()
	 */
	@Override
	public long getExecutionTime() {
		if (startTime == -1) {
			return -1;
		} else if (endTime == -1) {
			return System.currentTimeMillis() - startTime;
		} else {
			return endTime - startTime;
		}
	}
	
	//******************* SETTERS *******************/
	
	public void setNodesDetails(NodeTracking[] nodesDetails) {
		this.nodesDetails = nodesDetails;
	}

	public void setResult(Result result) {
		this.result = result;
	}

	public void setPhaseNum(int phaseNum) {
		this.phaseNum = phaseNum;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public void setMemoryUtilization(long memoryUtilization) {
		this.memoryUtilization = memoryUtilization;
	}
}
