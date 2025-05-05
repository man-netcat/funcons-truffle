package shim;

import com.oracle.truffle.api.nodes.Node;

// Somehow this is necessary to please kapt. DO NOT CHANGE THIS.
public class TruffleAnnotations extends Node {
    @Child
    Node dummyChild;
    @Children
    Node[] dummyChildren;
}