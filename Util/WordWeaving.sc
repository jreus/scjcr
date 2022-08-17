/* -------------------------------------------
Wordweaving
Live Coding environment

(C) 2020 Jonathan Reus

----------------------------------------------*/

WordWeaving : TextView {
	var <>verbose = true;
	var <>evalAction;

	*new {|parent, bounds|
		^super.new(parent, bounds).init;
	}

	init {
		this.deleteOnClose = true;
		this.verbose = true;
		this.keyDownAction = nil;
		this.keyUpAction = nil;
		this.evalAction = {|ww, line| "Evaluate '%'".format(line).warn };
	}

	// Get line
	line {|lineNumber=0|
		var tmp, txt = this.string.split($\n);
		if(txt.size < lineNumber) {
			tmp = Array.newClear(lineNumber).collect({|it,idx|
				if(txt[idx].notNil) {
					txt[idx];
				} {
					$\n;
				};
			});
			txt=tmp;
		};
		^txt[lineNumber];
	}

	// Get Line(s)
	lines {|lineStart=0, lineEnd|
		if(lineEnd.isNil.or { lineEnd < lineStart }) { lineEnd = lineStart };
		^this.string.split($\n)[lineStart..lineEnd];
	}


	line_ {|lineNumber=0, str=""|
		var tmp, txt = this.string.split($\n);
		if(txt.size < lineNumber) {
			tmp = Array.newClear(lineNumber).collect({|it,idx|
				if(txt[idx].notNil) {
					txt[idx];
				} {
					$\n;
				};
			});
			txt=tmp;
		};
		txt[lineNumber] = str;
		this.string = txt.join($\n);
	}

	// Write something at a given line number
	// If the line doesn't exist, it will be created
	// Mode:
	//    \insert ~ insert, moving forward whatever is there
	//    \overwrite ~ erase the entire line and overwrite
	//    \append ~ append to the end of the line
	writeAtLine {|str, lineNumber=0, offset=0, mode=\insert|
		var ch, neededLines, overwrite;
		ch = this.getCharPosIterative(lineNumber);
		neededLines = ch[1];
		ch = ch[0];
		if(ch.isNil) { // extend the document
			"extra lines needed %".format(neededLines).warn;
			ch = this.string.size;
			offset=0;
			neededLines.do {|idx|
				str = "\n"++str;
				ch=ch+1;
			};
		};
		//overwrite=str.size;
		switch(mode,
			\insert, {
				overwrite=0;
			},
			\overwrite, {
				var endline=ch;
				while({(endline < this.string.size) && (this.string[endline] != $\n)},{
					endline=endline+1;
				});
				overwrite=endline-ch;
				offset=0;
			},
			\append, {
				// get the current line
				while({(ch < this.string.size) && (this.string[ch] != $\n)},{
					ch=ch+1;
				});
				overwrite=0;
				offset=0;
			},
			{ // default
				Error("Invalid mode %".format(mode)).throw;
			};
		);
		this.setString(str, ch+offset, overwrite);
	}

	// Zero indexed line numbers, always
	// returns [charpos, remaining lines]
	getCharPosIterative {|lineNumber=0|
		var idx=0, ch=0, ln=0, max, remaining=nil;
		max=this.string.size;
		while {ln < lineNumber && (ch < max)} {
			if(this.string[ch] == $\n) {
				ln=ln+1
			};
			ch=ch+1;
		};
		if(ch==max) {
			ch=nil;
			remaining = lineNumber-ln;
		};
		^[ch, remaining];
	}

	// Get line and its character position
	getLineAndCharPos {|lineNumber=0|
		var lines, char=0;
		lines = this.string.split($\n)[..lineNumber];
		lineNumber.do {|idx|
			char = char + lines[idx].size + 1; // add 1 for \n character
		};
		^[char, lines[lineNumber]];
	}

	// Get start and end lines of selection (0-indexed)
	// if there is no selection gives the line where the cursor is.
	selectionLineNumbers {
		var ln=1, ch=1, str;
		var stln=1, endln=1, stch, endch;
		stch = this.selectionStart;
		endch = stch + this.selectionSize;
		if(this.string.size >= 2) {
			while({ ch <= endch }, {
				if(this.string[ch-1].ascii == 10) {
					ln=ln+1;
				};
				if(ch == stch) {
					stln = ln;
				};
				if(ch == endch) {
					endln = ln;
				};
				ch=ch+1;
			});
		};
		^[stln, endln];
	}


	// ---------------------------------------------------------------------------------
	// Override keyDownEvent and keyUpEvent to intercept specific key combinations
	// before they reach the C++ QT implementation

	keyDownEvent {|char, modifiers, unicode, keycode, key, spontaneous|
		if(this.verbose == true) {
			["keyDownEvent", char, modifiers, unicode, keycode, key, spontaneous].postln;
		};
		if(modifiers == 33554432 and: (unicode == 13)) { // SHIFT-ENTER EVALUATE
			try {
				this.evalAction.(this);
			} {|err|
				switch(err.species.name,
					'LangError', { err.errorString.error },
					{
						err.postProtectedBacktrace;
						err.errorString.postln;
					},
				);
			};
			^true;
		};
	}


	keyUpEvent {|char, modifiers, unicode, keycode, key, spontaneous|
		if(this.verbose == true) {
			["keyUpEvent", char, modifiers, unicode, keycode, key, spontaneous].postln;
		};
		if(modifiers == 33554432 and: (unicode == 13)) {
			// IGNORE SHIFT-ENTER UP EVENT
			^true;
		};
	}

	// END key event overrides
	// ---------------------------------------------------------------------------------
}

// ERROR TYPES ...
LangError : Error {
	errorString {
		^what;
	}
}
