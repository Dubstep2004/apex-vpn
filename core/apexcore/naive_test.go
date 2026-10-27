//go:build with_naive_outbound

package apexcore

import "testing"

func TestNaiveConfig(t *testing.T) {
	nodes := parseNodes(t, "naive+https://u:p@nv.example.com:443#Naive\nnaive+quic://u:p@nq.example.com#NaiveQ")
	checkAll(t, nodes, "")
}
