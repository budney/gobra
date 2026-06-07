// Any copyright is dedicated to the Public Domain.
// http://creativecommons.org/publicdomain/zero/1.0/

package pkg

//@ ensures ret == 7
//@ decreases
//@ verified
func constSeven() (ret int) {
	ret = 7
	return ret
}
