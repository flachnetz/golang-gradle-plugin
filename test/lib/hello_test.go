package lib

import "testing"

func TestGoodFirst(t *testing.T) {

}

func TestGoodSecond(t *testing.T) {

}

func TestBadFirst(t *testing.T) {
	t.Error("Square had only three corners.")
}

func TestBadSecond(t *testing.T) {
	t.Error("Expected pi to be 3.")
}


