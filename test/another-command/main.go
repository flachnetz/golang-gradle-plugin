package main // import "srv-git-01-hh1.alinghi.tipp24.net/iwg/gradle-golang/test/another-command"

// #include "test.h"
import "C"

import "fmt"
import "unsafe"
import "srv-git-01-hh1.alinghi.tipp24.net/iwg/gradle-golang/test/lib"

func superFastSum(values []int64) int64 {
	if len(values) == 0 {
		return 0
	}

	valuePtr := (*C.int64_t)(unsafe.Pointer(&values[0]))
	result := C.fast_sum(valuePtr, C.int64_t(len(values)))
	return int64(result)
}

func main() {
	fmt.Println("Yes, i am the other binary.")
	fmt.Println("Also, the super fast sum of 1 + 2 is", superFastSum([]int64{1, 2}))
	lib.Hello()
}
