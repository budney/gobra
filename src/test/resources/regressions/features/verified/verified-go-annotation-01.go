// Any copyright is dedicated to the Public Domain.
// http://creativecommons.org/publicdomain/zero/1.0/

// Test that //@ verified in a .go file is processed correctly by Gobrafier.

package pkg

//@ requires x >= 0
//@ ensures ret == x + 1
//@ decreases
//@ verified
func goIncrement(x int) (ret int) {
	ret = x + 1
	return ret
}

//@ requires n >= 0 && goIncrement(n) > 3
//@ ensures result > 3
func useGoIncrement(n int) (result int) {
	result = goIncrement(n)
	return result
}
