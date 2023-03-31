+ SimpleNumber {
	//((-100..100) / 10 ).collect({|item,i| peak(item, 10)}).plot;
	//((-100..100) / 10 ).collect({|item,i| valley(item, 2)}).plot;

	// Maps -1 to 1
	peak {|width=1|
		var result, fromval;
		fromval = this / width;
		if(fromval <= 0) {
		result = (1 / (1 + exp(0 - fromval)))
		} {
		result = (1 / (1 + exp(fromval)));
		}
		^result;
	}

	// Maps -1 to 1
	valley {|width=1|
		var result, fromval;
		fromval = this / width;
		if(fromval >= 0) {
		result = (1 / (1 + exp(0 - fromval)))
		} {
		result = (1 / (1 + exp(fromval)));
		}
		^result;
	}

	sigmoid {
		^1 / (1 + (-1 * this).exp)
	}

	step {
		^ if(this > 0) {1} {0};
	}

	relu {
		^ if(this > 0) {this} {0};
	}

}





// Some useful additions to Integer
+ Integer {

	/***
	Get the integer as two bytes
	***/
	toBytes {|whichbyte=(-1)|
	var result = [this & 0xFF,((this >> 8) & 0xFF)];
	if (whichbyte == 0 || whichbyte == 1) {
		result = result[whichbyte];
	};
	^result;
	}

}


