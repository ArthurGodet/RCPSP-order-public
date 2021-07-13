/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2020, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package alldifferentprec;

import org.chocosolver.util.objects.graphs.DirectedGraph;
import org.chocosolver.util.objects.setDataStructures.ISet;
import org.chocosolver.util.objects.setDataStructures.SetFactory;
import org.chocosolver.util.objects.setDataStructures.SetType;

/**
 * Directed graph implementation : arcs are indexed per endpoints
 * @author Jean-Guillaume Fages, Xavier Lorca
 */
public class DirectedGraph2 extends DirectedGraph {

    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    private ISet[] successors;
    private ISet[] predecessors;
    private ISet nodes;
    private int n;

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    /**
     * Creates an empty graph.
     * Allocates memory for n nodes (but they should then be added explicitly,
     * unless allNodes is true).
     *
     * @param n        maximum number of nodes
     */
    public DirectedGraph2(int n) {
        super(n, SetType.BITSET, true);
        this.n = n;
        predecessors = new Set_BitSet[n];
        successors = new Set_BitSet[n];
        for (int i = 0; i < n; i++) {
            predecessors[i] = new Set_BitSet(0);
            successors[i] = new Set_BitSet(0);
        }
        this.nodes = SetFactory.makeConstantSet(0, n-1);
    }
}
