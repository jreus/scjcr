/*_____________________________________________________________
Syn.sc
A compact synthdef manager

Copyright (C) 2018 Jonathan Reus
http://jonathanreus.com

based on Thesaurus, by Darien Brito https://github.com/DarienBrito/dblib

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Metadata Syntax:

/*
@synth
@shortdesc Simple Sinosc
@desc A monophonic sine oscillator with frequency and amp control.
@types Pitched, Sinus
*/
(
SynthDef('mysin', {|out=0, amp=1.0, freq=220, gate=1|
  Out.ar(out, SinOsc.ar(freq) * amp * EnvGen.ar(Env.perc, gate, doneAction: 2));
}).add;
);

/*
@example mysin
*/
x = Synth('mysin');
x.free;
(instrument:'mysin', freq: "G4".notecps, dur: 3, amp: 0.5).play;


________________________________________________________________*/



/*
@class
Manages a library of synth definitions.
Ths SynthLibrary looks for synthdef files
(written as plain SC expressions) in the
'SynthDefs' directory inside a provided library path.

SynthDefs must be defined using 'singlequote' symbol notation.

@usage

x = Syn.load();
x.gui;
*/
Syn {
	classvar <>defaultSynthPath;
	classvar <synthdefs, <>synthdefsPath, <synthdefFilePaths, <fileNames;
	classvar <guiWindow;
	classvar <allNames, <allTypes;

	*initClass {
		defaultSynthPath = "/home/jon/Dev/_LIB/SC_Synthesis/SynthDefs";
	}

	*load { |synthDefsPath, server|
		var sp, stats;
		if (synthDefsPath.isNil) {
			Error("No synthdefs search path provided to Syn.load").throw;
		} {
			synthdefsPath = synthDefsPath;
		};
		if(server.isNil) { server = Server.default };
		fileNames = List();
		allNames = List();
		allTypes = List();
		synthdefs = Dictionary.new; // add synthdefs hashed on name
		synthdefFilePaths = ( synthdefsPath +/+ "*").pathMatch;

		/*** PARSE SYNTH LIBRARY FILES ***/
		stats = [0, 0]; // files / synths
		synthdefFilePaths.do{|filepath|
			var kw1,kw2,kw3; // keyword locations
			var fulltext,tmp1,tmp2;
			fulltext = File.readAllString(filepath);
			kw1 = fulltext.findAllRegexp("@synth");
			kw2 = fulltext.findAllRegexp("SynthDef");
			kw3 = fulltext.findAllRegexp("\.add;");
			kw3 = kw3 + 4; // move indexes forward to the end of the line where .add; was found

			// error checks
      if((kw1.size != kw2.size).or {kw2.size != kw3.size}) {
        var err = "Syntax error in synthdefs library file % \nNumber of @synth tags and SynthDefs do not match.".format(filepath);
        err.error; err.throw;
        ^nil;
			};

			kw1.size.do {arg idx; // check order of keyword locations follows sequence: @synth -> SynthDef -> .add;
				if ((kw1[idx] > kw2[idx]).or{ kw2[idx] > kw3[idx] }) {
					var err = "Syntax error in synthdefs library file % \nNumber of @synth tags and SynthDefs do not match.".format(filepath);
          err.error; err.throw;
          ^nil;
				};
			};

			// interpret synthdefs and parse metadata
      kw1.size.do {|idx|
				var metatext, deftext, name, synthdesc, info;
        metatext = fulltext[kw1[idx]..(kw2[idx]-5)];
				deftext = fulltext[kw2[idx]..kw3[idx]];
        name = deftext.findRegexp("SynthDef[(]['\]([A-Za-z0-9_]+)[']?");
        if(name.size == 0) {
          "Syntax error in SynthDef expression\nin %".format(filepath).throw;
        };
        name = name[1][1].asSymbol;
        deftext.interpret; // interpret Synthdef & create SynthDesc, throws an error if the server is not running

				synthdesc = SynthDescLib.global.synthDescs[name];
				info = SynthInfo.new(synthdesc);
				info.parseDocString(metatext, filepath);
				synthdefs[name] = info;
				allNames.add(name);
				stats[1] = stats[1] + 1; // add synth
			};
			fileNames.add(PathName.new(filepath).fileName);
			stats[0] = stats[0] + 1; // add file
		};

		// Collect all types
		allTypes = Set.new;
		synthdefs.values.do {|sinfo|
			sinfo.types.do {|type|
				allTypes.add(type);
			};
		};
		allTypes = allTypes.asList.sort;
		allNames = allNames.sort;

		"... Parsed % synthdefs in % files ...".format(stats[1], stats[0]).warn;
		/*** END PARSE SYNTH LIBRARY FILES ***/
	}

	// Opens a window to navigate synths and file locations of synth definitions.
	*gui {
		var decorator, styler, subStyler, childView, childDecorator, substyler;
		var subView, btn, key, s_info;
		var searchText, searchList, searchTypeDropdown1, searchTypeDropdown2;
		var width=300, height=400, lineheight=15;
    var listheight=100, textheight=250;
		var findFunc;
		if(synthdefs.isNil) { this.load };

		// Master window
		if(guiWindow.notNil) { guiWindow.close };
    guiWindow = Window("Synth Catalog", Rect(0,0,(width+20), height));
		styler = GUIStyler(guiWindow);

		// Child window
		childView = styler.getView("SynthDefs", guiWindow.view.bounds, scroll: true);
		//childDecorator = decorator;

		// Search
		searchText = TextField(childView, width@lineheight);

		searchTypeDropdown1 = PopUpMenu(childView, (width/2)@lineheight)
		.items_(allTypes.putFirst("--").asArray).stringColor_(Color.white)
		.background_(Color.clear);

		searchTypeDropdown2 = PopUpMenu(childView, (width/2)@lineheight)
		.items_(allTypes.putFirst("--").asArray).stringColor_(Color.white)
		.background_(Color.clear);

    searchList = ListView(childView, width@listheight)
		.items_(allNames.asArray)
		.stringColor_(Color.white)
    .font_(styler.font)
		.background_(Color.clear)
		.hiliteColor_(Color.new(0.3765, 0.5922, 1.0000, 0.5));


		findFunc = { |name|
			var type1=nil, type2=nil, filteredNames;
			if(searchTypeDropdown1.value > 0) {
				type1 = searchTypeDropdown1.item;
			};
			if(searchTypeDropdown2.value > 0) {
				type2 = searchTypeDropdown2.item;
			};
			filteredNames = allNames.selectAs({ |item, i|
				var result,t1,t2;
				t1 = synthdefs[item].isType(type1, type2);
				if(name=="") {
					t2 = true;
				} {
					t2 = item.asString.containsi(name);
				};
				t1 && t2;
			}, Array);
			searchList.items_(filteredNames)
		};

		searchText.action = { |field |
			var search = field.value; findFunc.(search);
		};
		searchText.keyUpAction = {| view| view.doAction; };

		searchTypeDropdown1.action = {
			var search = searchText.value; findFunc.(search);
		};

		searchTypeDropdown2.action = {
			var search = searchText.value; findFunc.(search);
		};

		subStyler = GUIStyler(childView);
    subView = subStyler.getView("Subwindow", Rect(0,0,width, lineheight+textheight));

		searchList.action_({ |sbs| // action when selecting items in the search list
			var key, s_info, btn, txt, extext, synthdef = sbs.items[sbs.value];
			synthdef.postln;

			(instrument: synthdef, out: 0).play; // Play the synth with default values.

			subView.removeAll; // remove subViews
			subView.decorator = FlowLayout(subView.bounds);

			s_info = synthdefs[synthdef];
			key = s_info.name;

			btn = subStyler.getSizableButton(subView, key.asString, size: 100@lineheight);
			btn.action = { |btn|
				Synth(key);
			};

			btn = styler.getSizableButton(subView, "source", size: 50@lineheight);
			btn.action = {|btn|
        [s_info, s_info.filePath].postln; Document.open(s_info.filePath);
			};


			btn = subStyler.getSizableButton(subView, "insert", size: 40@lineheight);
			btn.action = { |btn|
				"Insert for %".format(key).postln;
			};

			btn = subStyler.getSizableButton(subView, "pattern", size: 40@lineheight);
			btn.action = { |btn|
				"Pattern for %".format(key).postln;
			};


			txt = subStyler.getTextEdit(subView, width@textheight);
			File.readAllString(s_info.filePath).split($@).do {|chunk,i|
				var found = chunk.findRegexp("^example "++ key.asString ++"[\n][*][/][\n](.*)");
				if(found != []) {
					found = found[1][1];
					txt.string_(found[..(found.size - 5)]);
				};
			};
		});

		guiWindow.front;
	}

	*browseSynths{
		SynthDescLib.global.browse;
	}

	/*
	@return a set containing all synthdef names
	*/
	*names {
		if(synthdefs.isNil) { this.load };
		^synthdefs.keys;
	}

	/*
	@return a list containing all synthdef types/categories
	*/
	*types {
		if(synthdefs.isNil) { this.load };
		^allTypes;
	}

	*count {
		^synthdefs.size;
	}
}



/*
@class
Wraps a SynthDesc for the storage of custom metadata and example presets.
*/
SynthInfo {
	var <synthDesc;
	var <>metaData;
	var <>examples;

	*new {arg sdesc;
		^super.newCopyArgs(sdesc);
	}

	/*
	Expects a string of the format:

	@synth
	@shortdesc Pulse Bass
	@desc Bass synthesizer derived from a pulse train.
	@types Bass, Lead
	*/
	parseDocString {arg metaStr, filePath=nil;
		var tmp;
		if(metaData.isNil) { metaData = Dictionary.new; };
		if(filePath.notNil) { metaData[\filePath] = filePath; };
		tmp = metaStr.split($\@)[2..];
		tmp.do {arg ml;
			var key,val,regexp;
			//regexp = "^([A-Za-z0-9_]+) ([\"A-Za-z0-9 \t\r\n!\#$%&`()*\-,:;<=>?\[\\\]^_{|}~]+)";
			regexp = "^([A-Za-z0-9_]+) ([A-Za-z0-9 \t\r\n!\#$%&`()*\-,:;<=>?\[\\\]^_{|}~]+)";
			ml = ml.findRegexp(regexp);
			key = ml[1][1].asSymbol;
			val = ml[2][1];
			if(key == 'types') { // parse types
				val = val.split($\n)[0].split($,).collect({|item, idx|
					item.stripWhiteSpace.asSymbol;
				});
			};
			metaData[key] = val;
		};
	}

	/*
	@returns true if this synth matches the given types, returns true if arguments are nil
	*/
	isType {|...args|
		var found, result = true;

		args.do {|testFor|
			if(testFor.isNil) {
				found = true;
			} {
				found = false;
				this.types.do {|type|
					if(testFor == type) {
						found = true;
					};
				};
			};
			if(found.not) {
				result = false;
			};
		};
		^result;
	}




	doesNotUnderstand { | selector...args |
		var result;

		// Exists in SynthDesc?
		if(synthDesc.respondsTo(selector)) {
			result = synthDesc.performList(selector, args);
		} {
			// Exists in metaData?
			result = metaData.atFail(selector, {
				error("Selector % does not exist.".format(selector));
			});
		}

		^result;
	}
}

