/*_____________________________________________________________
Smpl.sc

Sample library manager and buffer utilities

(C) 2018 Jonathan Reus / GPLv3
http://jonathanreus.com

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program.  If not, see http://www.gnu.org/licenses/

________________________________________________________________*/




/*-------------------------------------------------
Features / Todos
* sort by # channels and sample rate and length range in Smpl
* ability to add tags in gui
* ability to save & load tags and markers in metadata files
* a compact syntax for playing samples inside functions & routines
* similar to say2 for speech. e.g. to be able to be used easily in a composition / scheduling system



/* SMPL TODO

SMPL
TODO: This still needs a lot of work to get functional..
Looping doesn't quite work yet..


I'm in the middle of working on Smpl's playback system..
keep working on that.. I'm making a SampleFileView linked to
SampleFile, right now I have the synthdefs telling me position
and when they are done via SendTrig and OSCfuncs...
the gui part is not done on SampleFile, and once it is,
this gui needs to be integrated with Smpl's gui...
I think that play / stop operations are done ON the SampleFile
and automagically reflected in the respective view... however,
a loop flag must be set manually on the SampleFile

1. Rehash scenes for my new composition concept...
2. make Scenes work more like Macros, Syn, Smpl, & other modules in usage/syntax
3. Allow navigating through scene instances (doesn't seem to work now?)

*/

// TODO: Add a Smpl.find function that returns something similar to what you're looking for, or an array of such


-------------------------------------------------*/



/******************************************************************

@usage

Smpl.lazyLoadGlobal = false;
Smpl.lazyLoadLocal = true;
Smpl.load(verbose: true); // loads global sample library

Smpl.at("drum001"); // get sample by id, or search library by various functions
// e.g. by type, by directory, etc..

Smpl.gui; // open gui editor

*******************************************************************/

Smpl {
	classvar <samples; // all available sample files by id
	classvar <samplesByGroup; // available sample files, by group name
	classvar <allGroups, <allTags; // lists of all available sample groups and tags
	classvar <localSamples;
	classvar <globalSamples;
	classvar <>globalSamplesPath; // global samples directory
	classvar <>localSamplesPath; // local samples directory

	classvar <activeServer; // server where buffers are allocated and loaded, should be set on load
	classvar <listenerGroup; // a group at the head of the server where recording synths go

	// gui variables
	classvar <win, <playLooped=false, <autoPlay=true;
	classvar <currentSample, <sampleFilePlayer, <cursorRoutine, <currentTimePosition=0;

	// boolean toggles lazyloading of samples, false by default. If true will save some ram by only loading samples when necessary
	// However, keep in mind that samples are loaded into buffers on the server asynchronously from other language instructions,
	// and thus you must be wary of performing operations on buffer variables still holding nil.
	classvar <>lazyLoadGlobal=true, <>lazyLoadLocal=false;

	// cache of pre-allocated buffers used for live sampling
	classvar <liveSampleBuffers;
	classvar <liveSamplesByName;
	classvar <numLiveBuffers=10;
	classvar <nextLiveBufferAllocIndex=0;
	classvar <liveBufferDurationSeconds=4;

	*initClass {
		samples = Dictionary.new;
		samplesByGroup = Dictionary.new;
		globalSamples = Dictionary.new;
		localSamples = Dictionary.new;
		liveSampleBuffers = List.new;
		liveSamplesByName = Dictionary.new;
		allGroups = List.new;
		allTags = List.new;
		globalSamplesPath = "~/assets/_samples/".absolutePath;
		this.pr_loadEventTypes;
	}

	*pr_loadEventTypes {
		// General sample event for buffer playback
		"Smpl: Adding Events: \\bufPlay \\smpl".warn;

		Event.addEventType(\bufPlay, {|s|
			var chans = ~buf.numChannels;
			if(~end.isNil) { ~end = ~buf.numFrames };
			/* If I want to use the functionality of \note events then I can't depend on freq being set here..
			   See the note model in the Event guides...
			if(~dur.isNil.or { ~dur < 0 }) {
				var bufratescale = s.sampleRate / ~buf.sampleRate;
				var prate = ~rate * (~freq / ~rootPitch);
				~dur = ((~end - ~start).abs * ~loops) / s.sampleRate / bufratescale / prate;
				if((~atk + ~rel) >= ~dur) {
					var ratios = [~atk, ~rel].normalizeSum;
					~atk = ~dur * ratios[0];
					~rel = ~dur * ratios[1];
					"atk+rel longer than dur, recalculating atk: %  rel: %".format(~atk, ~rel).warn;
				};
				~dur = ~dur - ~rel;
			};
			*/

			//"smpl %   start %   end %".format(~smpl, ~start, ~end).warn;

			if(chans == 1) {
				~instrument = \smpl_pitchedSample1ch;
			} {
				~instrument = \smpl_pitchedSample2ch;
			};

			~type = \note;
			currentEnvironment.play;
		}, (rootPitch: \c5.f, scale: Scale.major, root: 0, octave: 5, rate: 1.0, atk: 0.01, rel: 0.01, start: 0, outbus: 0, loops: 1, dur: -1));

		// Playback event for Smpl library
		Event.addEventType(\smpl, {|s|
			if(~smpl.isKindOf(SequenceableCollection).and{~smpl.class != String}) {
				// ~smpl is a sample playback spec [sid, numchannels, start, end, ...]
				~start = ~smpl[2];
				if(~smpl[3] >= 0) {
					~end = ~smpl[3];
				};
				~smpl = ~smpl[0];
			};
			~buf = Smpl.buf(~smpl);
			~type = \bufPlay;
			currentEnvironment.play;
		}, (dur: -1));
	}

	// Load Synth Defs used by Smpl Lib for playback and auditioning.
	*pr_loadSynthDefs {|serv|
		SynthDef('smpl_pitchedSample1ch', {|amp=0.5, rate=1, freq=261.62556, rootPitch=261.62556, start=0, end, buf, out, pan=0, atk=0.001, rel=0.001, co=20000, rq=1.0, gate=1, loops=1, autogate=0|
			var sig, playhead, dur, prate;
			var t_cnt = 0, t_loop = 0, mute=1;
			prate = rate * (freq / rootPitch);
			dur = (end - start).abs / SampleRate.ir / BufRateScale.ir(buf) / prate;
			t_loop = Impulse.ar(dur.reciprocal);
			t_cnt = PulseCount.ar(t_loop);
			mute = (t_cnt <= loops); // do not trigger if count is above number of loops
			t_loop = mute * t_loop;
			playhead = EnvGen.ar(Env([0, start, end],[0, dur]), t_loop);
			sig = BufRd.ar(1, buf, playhead);

			// release gate on mute if autogate is 1
			gate = Select.kr(autogate, [gate, mute]);

			// Free on gate release
			sig = sig * EnvGen.ar(Env.asr(atk, 1.0, rel), gate: gate, doneAction: 2);

			// free on silence...
			//DetectSilence.ar(sig, 0.0001, 0.5, doneAction: Done.freeSelf);

			sig = Mix.ar(sig);
			sig = BLowPass4.ar(sig, co, rq);
			Out.ar(out, Pan2.ar(sig,pan) * amp);
		}).add;

		SynthDef('smpl_pitchedSample2ch', {|amp=0.5, rate=1, freq=261.62556, rootPitch=261.62556, start=0, end, buf, out, gate=1, atk=0.01, pan=0.0, rel=0.01, co=20000, rq=1.0, loops=1, autogate=0|
			var sig, playhead, dur, prate;
			var t_cnt = 0, t_loop = 0, mute=1;
			prate = rate * freq / rootPitch;
			dur = (end - start).abs / SampleRate.ir / BufRateScale.ir(buf) / prate;
			t_loop = Impulse.ar(dur.reciprocal);
			t_cnt = PulseCount.ar(t_loop);
			mute = (t_cnt <= loops);
			t_loop = mute * t_loop;
			playhead = EnvGen.ar(Env([0, start, end],[0, dur]), t_loop);
			sig = BufRd.ar(2, buf, playhead);

			// release gate on mute if autogate is 1
			gate = Select.kr(autogate, [gate, mute]);

			// Free on gate release
			sig = sig * EnvGen.ar(Env.asr(atk, 1.0, rel), gate: gate, doneAction: 2);
			// Alternatively, free on silence...
			//DetectSilence.ar(sig, 0.0001, 0.5, doneAction: Done.freeSelf);
			sig = BLowPass4.ar(sig, co, rq);
			sig = Pan2.ar(sig[0], (pan-1).clip(-1,1)) + Pan2.ar(sig[1], (pan+1).clip(-1,1));
			Out.ar(out, sig * amp);
		}).add;

	}

	*pr_allocateLiveSampleBuffers {|serv|

		if(listenerGroup.isNil) {
			listenerGroup = Group.new(serv, \addBefore);
		};

		numLiveBuffers.do {|idx|
			var group, newid, groupid = \livesample;
			newid = "ls%".format(idx);
			group = samplesByGroup.at(groupid);
			if(group.isNil) {
				group = Dictionary.new;
				samplesByGroup.put(groupid, group);
			};

			Buffer.alloc(serv, serv.sampleRate * liveBufferDurationSeconds, 1, {|bf|
				var ls;
				bf.sampleRate = serv.sampleRate;
				liveSampleBuffers.add(bf);
			});
		};
	}

	// private method
	*pr_checkServerAlive {|errorFunc|
		activeServer = activeServer ? Server.default;
		if(activeServer.serverRunning.not) {
			error("Cannot Allocate Buffers: Boot the Server First!");
			errorFunc.value;
		};
	}

	// Loads group into memory and returns sample names in undefined order
	*loadGroupAndGetNames {|gr, serv|
		gr = samplesByGroup.at(gr.asSymbol);
		if(gr.notNil) {
			^gr.values.sort({|a,b| a.name < b.name }).collect {|samplefile|
				samplefile.loadFileIntoBuffer(serv);
				samplefile.name; // return id
			};
		} {
			^nil;
		};
	}

	*samplesForGroup {|gr|
		gr = samplesByGroup.at(gr.asSymbol);
		if(gr.notNil) {
			^gr.values.sort({|a,b| a.name < b.name });
		} {
			^nil;
		};
	}

	*sampleNamesForGroup {|gr|
		gr = samplesByGroup.at(gr.asSymbol);
		if(gr.notNil) {
			^gr.keys.asList.sort;
		} {
			^nil;
		};
	}


	// A global samples directory will be searched for at globalSamplesPath
	// All directories in the array localSamplePaths will be loaded
	// If a server is not provided, server will be the default server.
	*load {|server=nil, verbose=false, limitLocal=nil, limitGlobal=nil, localSamplePaths=nil, doneFunc=nil|
		var samplePath, loaded=0;
		activeServer = server;
		this.pr_checkServerAlive({^nil});
		this.pr_loadEventTypes();

		"LOAD SYNTHDEFS".warn;
		this.pr_loadSynthDefs(server);

		"ALLOCATE LIVESAMPLE BUFFERS".warn;
		this.pr_allocateLiveSampleBuffers(server);

		"LOAD SAMPLE PLAYBACK SYNTHDEFS".warn;
		SampleFile.loadSynthDefs(server);

		"LOAD LIVESAMPLE CAPTURE SYNTHDEFS".warn;
		LiveSample.loadSynthDefs(server);

		if(File.exists(globalSamplesPath).not) { File.mkdir(globalSamplesPath) };

		if(localSamplePaths.notNil) { // Load sample directories
			"\nSmpl: Loading Local Sample Banks ...".postln;
			localSamplePaths.do {|samplePath|
				samplePath = PathName.new(samplePath);
				loaded = loaded + this.pr_loadSamples(samplePath, lazyLoadLocal, verbose, limitLocal - loaded);
				".%.".format(samplePath.folderName).post;
			};
			"\nSmpl: % samples loadefileNameWithoutExtensiond".format(loaded).postln;
		} { "Smpl: No local sample paths provided".postln };


		// Load samples from global directory
		"\nSmpl: Loading Global Samples at %".format(globalSamplesPath).postln;
		samplePath = PathName.new(globalSamplesPath);
		loaded = this.pr_loadSamples(samplePath, lazyLoadGlobal, verbose, limitGlobal, "_glo");
		"\nSmpl: % samples loaded".format(loaded).postln;

		// Collect groups and tags
		allGroups = Set.new;
		allTags = Set.new;

		samples.do {|sf|
			allGroups.add(sf.library);
			sf.tags.do {|tag| allTags.add(tag) };
		};

		allGroups = allGroups.asList.sort;
		allTags = allTags.asList.sort;
		"\nSmpl: Library loaded % samples".format(samples.size).postln;
		if(doneFunc.notNil) { doneFunc.value() };
	}

	// private method
	*pr_loadSamples {|samplePath, lazyLoad, verbose, limit|
		var res, tmplim = limit;
		res = block {|limitReached|
			samplePath.filesDo {|path,i|
				var res;
				res = this.pr_loadSampleAtPathName(path, lazyLoad, verbose, samplePath);
				if(res.notNil) {
					if(verbose) {
						"%[%: %]".format(res.name, path.folderName, path.fileName).postln;
					} { if(i%100 == 0) { ".".post } };

					tmplim = tmplim - 1;
					if(tmplim <= 0) { limitReached.value };
				};
			};
		};

		if(res == \limit) {
			"limit % reached, some samples were not loaded".format(limit).warn;
		};

		^(limit-tmplim);
	}

	*pr_loadSampleAtPathName {|path, lazyLoad, verbose, sampleSourcePath|
		var sf; // samplefile
		sf = SampleFile.openRead(path);

		if(sf.notNil) {
			var id, group, groupId, preStr = ".", tmp1;
			var sampleSource = \UNKNOWN;

			id = path.fileNameWithoutExtension.replace(" ","");
			if(samples.at(id).notNil) {
				if(samples.at(id).path != sf.path) { // if paths are not equal, modify id to avoid doubles
					id = "%_%".format(sf.folderGroups.last.replace(" ","_"), id);
				};
			};

			sf.name = id;
			samples.put(id, sf);

			if(sampleSourcePath.pathOnly.find(globalSamplesPath) == 0) {
				sampleSource = \GLOBAL;
				groupId = "_glo";
				globalSamples.put(id, sf);
			} {
				sampleSource = \LOCAL;
				groupId = "_loc";
				localSamples.put(id, sf);
			};

			// Create group id from file path
			tmp1 = path.allFolders.reverse;
			sampleSourcePath.allFolders.do {|sourceFolderName|
				if(sourceFolderName != tmp1.pop) {
						"DIRECTORY PATH MISMATCH '%' when SAMPLE DIRECTORY IS '%'".format(path, sampleSourcePath).throw;
				};
			};

			tmp1.reverse.do {|folderName|
				groupId = groupId++"--"++folderName;
			};

			groupId = groupId.asSymbol;
			group = samplesByGroup.at(groupId);
			if(group.isNil) { // new group
				group = Dictionary.new;
				samplesByGroup.put(groupId, group);
			};
			group.put(id, sf);
			sf.library = groupId.asString;

			if(lazyLoad == false) { // if lazyload not active, go ahead and load everything
				sf.loadFileIntoBuffer(activeServer);
				preStr = "...";
			};

			^sf;
		} {
			if(verbose) { "Smpl: % not found".format(path.fileName).warn };
			^nil;
		};
	}

	// When lazyloading is active, preload lets you preload a group of samples
	// provided as an array of sample ids
	*preload {|samples|
		samples.do {|name|
			var smp = this.samples[name];
			if(smp.isNil) {
				"Sample '%' does not exist, ignored".format(name).error;
			} {
				smp.loadFileIntoBuffer;
			}
		};
	}

	// Preload a set of samples into memory, organized as a kit
	// a kit has an id and is given as an array of sample specs
	// a sample spec is an array of the form [sid, numchannels, begin, end, ...]
	// where ... are additional fields, for example, the nickname for this sample in the kit
	*preloadKit {|kitid, kit|
		// TODO: ignore the id for now until the kit building system is implemented in the GUI
		kit.do {|spec|
			var smp = this.samples[spec[0]];
			if(smp.isNil) {
				"Sample '%' does not exist for kit '%', ignored".format(spec[0], kitid).error;
			} {
				smp.loadFileIntoBuffer;
			}
		}
	}

	// Preload a set of samples into memory, organized as a dictionary of kits
	// usually a composition or other project will consist of such a named set of kits
	*preloadKits {|kitsDict|
		kitsDict.keysValuesDo {|kitid, kit|
			Smpl.preloadKit(kitid, kit);
		}
	}

	// Returns the SampleFile for a given name
	*at {|name, autoload=true, loadAction|
		var sample, result = nil;
		this.pr_checkServerAlive({^nil});
		sample = this.samples[name];
		if(sample.isNil) {
			^nil
		};
		if(autoload) {
			if(sample.buffer.isNil) {
				sample.loadFileIntoBuffer(action:loadAction);
			};
		};
		^sample;
	}

	// returns the buffer for a given SampleFile id, loads the sample into
	// a buffer if not loaded
	*buf {|name, loadAction|
		^this.at(name, true, loadAction).buffer;
	}

	*path {|id|
		if(this.samples[id].notNil) {
			^this.samples[id].path;
		} {
			^nil;
		}
	}

	// Get the root pitch for a given sample
	// TODO: not implemented yet!
	*rootPitch {|id|
		if(this.samples[id].notNil) {
			^this.samples[id].rootPitch;
		} {
			^nil;
		};
	}


	// Can be called before catch to prepare a LiveSample by name for recording...
	// you can choose a specific livesample buffer (0-9) by index
	// or pass -1 to automatically choose the next one...
	*prepareLiveSample {|name, index=(-1)|
		var ls;
		if(name.notNil) {
			ls = liveSamplesByName.at(name);
			if(ls.isNil) {
				var lsbuf, group;

				// get a specific livesample slot by index, or choose the next one
				// based on allocation order
				if(index === -1) {
					// choose next based on allocation order
					index = nextLiveBufferAllocIndex;
					nextLiveBufferAllocIndex = ((nextLiveBufferAllocIndex + 1) % numLiveBuffers).asInteger;

				};

				// choose by specific index
				lsbuf = liveSampleBuffers[index];
				ls = LiveSample.new(name, lsbuf, activeServer, listenerGroup);
				ls.library = \livesample.asString;
				liveSamplesByName.put(name, ls);
				samples.put(name, ls);
				group = samplesByGroup.at(\livesample);
				group.put(name, ls);

			};
		};
		^ls;
	}

	// make a new sample on the fly, based on onset detection and thresh-holding
	// records a "LiveSample" to the given Smpl Lib name
	// if a LiveSample with that name doesn't already exist, one will be taken
	// from the pre-allocated livesample indexes in order of allocation
	// you can also optionally specify an index to use a specific livesample slot
	*catch {|name, in=0, ampthresh=0.01, silenceThresh=1, timeout=10, index=(-1)|
		var ls = this.prepareLiveSample(name, index);
		if(ls.notNil) {
			// listen on the given input bus for audio
			ls.catch(in, ampthresh, silenceThresh, timeout);
		};
		^ls;
	}

	// Start endless loop recording into a livesample buffer
	*catchStart {|name, in=0, timeout=300, index=(-1)|
		var ls = this.prepareLiveSample(name, index);
		if(ls.notNil) {
			// listen on the given input bus for audio
			ls.catchStart(in, timeout);
		};
		^ls;
	}

	// Stop endless loop recording into a livesample buffer
	*catchStop {|name|
		var ls;
		if(name.notNil) {
			var group;

			// Get livesample
			ls = liveSamplesByName.at(name);
			if(ls.notNil) {
				ls.catchStop();
			} {
				"No Smpl named '%' was found".format(name).error;
			};
		};
		^ls;
	}

	*gui {|alwaysOnTop=false|
		var styler, subStyler, decorator, childView, childDecorator, subView;
		var searchText, sampleList, autoPlayCB, txt;
		var searchGroupDropdown, searchTagDropdown;
		var findFunc, resetFunc;
		var width=400, height=800, lineheight=20;
		var sampleListHeight=300, subWinHeight;
		var lastClick = Process.elapsedTime; // used for doubleclick detection
		subWinHeight = height - sampleListHeight - (lineheight*3);

		this.pr_checkServerAlive({^nil});

		// Main Window
		if(win.notNil) { win.close };
		win = Window("Sample Lib", (width+10)@height);
		styler = GUIStyler(win); // initialize with master win
		win.view.decorator = FlowLayout(win.view.bounds);
		win.view.decorator.margin = 0@0;

		// Scrollable Inner View
		childView = styler.getView("SubView", win.view.bounds, scroll: false);
		childView.decorator = FlowLayout(childView.bounds);
		childDecorator = decorator;


		// Search by Name & Category
		searchText = TextField(childView, width@lineheight);

		searchGroupDropdown = PopUpMenu(childView, (width/2.1)@lineheight)
		.items_(allGroups.asArray.insert(0, "--")).stringColor_(Color.white)
		.background_(Color.clear);

		searchTagDropdown = PopUpMenu(childView, (width/2.1)@lineheight)
		.items_(allTags.asArray.insert(0, "--")).stringColor_(Color.white)
		.background_(Color.clear);

		sampleList = ListView(childView, width@sampleListHeight)
		.items_(samples.values.collect({|it| it.name }).asArray.sort)
		.stringColor_(Color.white)
		.background_(Color.clear)
		.hiliteColor_(Color.new(0.3765, 0.5922, 1.0000, 0.5));


		// Global options (Autplay, Loop, etc..)
		#autoPlayCB,txt = styler.getCheckBoxAndLabel(childView, "autoplay", lineheight, lineheight, 40, lineheight);
		autoPlayCB.value_(autoPlay).action_({|cb| autoPlay = cb.value });


		findFunc = { |name|
			var group=nil, tag=nil, filteredSamples=nil;
			if(searchGroupDropdown.value > 0) {
				group = searchGroupDropdown.item;
			};
			if(searchTagDropdown.value > 0) {
				tag = searchTagDropdown.item;
			};
			filteredSamples = samples.values.selectAs({|sf, i|
				var crit1, crit2, crit3;
				crit1 = if(group.isNil) { true } { sf.library == group };
				crit2 = sf.hasTag(tag);
				if(name=="") {
					crit3 = true;
				} {
					crit3 = sf.name.containsi(name);
				};
				crit1 && crit2 && crit3;
			}, Array);
			sampleList.items_(filteredSamples.collect({|it| it.name}).sort);
			resetFunc.();
			subView.removeAll;
			sampleList.value_(0);
		};

		searchText.action = { |st| findFunc.(st.value) };
		searchText.keyUpAction = {|st| st.doAction };
		searchGroupDropdown.action = { findFunc.(searchText.value) };
		searchTagDropdown.action = { findFunc.(searchText.value) };

		// Context-Dependent Sub-window
		subStyler = GUIStyler(childView); // configure substyler...?
		subView = subStyler.getView("Sample Info", Rect(0,0,width,subWinHeight), scroll: true);

		// SINGLE & DOUBLE CLICK ACTION FOR SAMPLE LIST
		sampleList.mouseUpAction_({|sl|
			var now, thresh = 0.3, sample;
			now = Process.elapsedTime;
			if((now - lastClick) < thresh) {
				// Double Click, copy sample name to open document
				var doc = Document.current;
				sample = sl.items[sl.value];
				doc.insertAtCursor("\"%\"".format(sample));
			};
			lastClick = now;

			if(subView.children.size == 0) { // a rare case, when after a search there is no active subview
				sl.action.value(sl);
			}
		});

		// NEW SAMPLEFILE SELECT ACTION
		sampleList.action_({ |sl|
			var sf, sfplayer, id, s_info, btn, txt, check, sfview, insertButton, insertEventBtn, insertArrayBtn, insertPathBtn;
			var playbut, tagfield;
			var playFunc, stopFunc;

			id = sl.items[sl.value];
			sf = samples.at(id);
			"Selected % %".format(id, sf.path).postln;
			// load sample into memory, prep for playback, & create subwindow when done

			sf.loadFileIntoBuffer(activeServer, {|buf|

				if(currentSample.notNil) {currentSample.cleanup};
				currentSample = sf;

				// everything else happens on appclock
				{
					// Build the Sample Info window
					subView.removeAll; // remove all views from the sub window
					subView.decorator = FlowLayout(subView.bounds);

					insertButton = subStyler.getSizableButton(subView, sf.name, size: (width-20-60-40-60-10)@lineheight); // name

					subStyler.getSizableText(subView, "%ch".format(sf.numChannels), 20); // channels


					subStyler.getSizableText(subView, sf.sampleRate, 40); // sample rate

					subStyler.getSizableText(subView, sf.headerFormat, 40); // format


					subStyler.getSizableText(subView, sf.duration.round(0.01).asString ++ "s", 60); // length

					subStyler.getHorizontalSpacer(subView, width);


					// Tags
					tagfield = TextField(subView, (width-40)@lineheight).string_("tags");

					btn = subStyler.getSizableButton(subView, sf.path, size: (width-20)@lineheight);
					btn.action_({|btn| // open in finder
						"open '%'".format(sf.path.dirname).unixCmd;
					});


					// event & array buttons
					insertEventBtn = subStyler.getSizableButton(subView, "event", size: 50@lineheight);
					insertArrayBtn = subStyler.getSizableButton(subView, "array", size: 50@lineheight);
					insertPathBtn = subStyler.getSizableButton(subView, "path", size: 50@lineheight);


					/*
					NEW TIMELINE VIEW (integrated into SampleFile)
					*/
					sfview = sf.getWaveformView(subView, width@200);

					// Insert a reference to the samplefile into the IDE
					insertButton.action_({|btn|
						var doc = Document.current;
						doc.insertAtCursor(
							"Smpl.at(\"%\")".format(sf.name)
						);
					});

					// insert a playable event into the IDE
					insertEventBtn.action_({|btn|
						var doc, insertString = "\n%\n".format(sfview.getEventStringForCurrentSelection);
						doc = Document.current;
						doc.insertAtCursor(insertString);
					});

					// insert an array with buffer and selection into the IDE
					insertArrayBtn.action_({|btn|
						var doc, arr = sfview.getSampleSpecForCurrentSelection;
						var insertString = "[\"%\", %, %, %]".format(arr[0], arr[1], arr[2], arr[3]);
						doc = Document.current;
						doc.insertAtCursor(insertString);
					});

					// insert a path to sample file into the IDE
					insertPathBtn.action_({|btn|
						var doc, insertString = sf.path;
						doc = Document.current;
						doc.insertAtCursor(insertString);
					});


					win.onClose = {|win|
						// TODO: clean up everything on window close
						// playnode
						// soundfile
						"CLEANUP SOUNDFILE ETC...".postln;
						sfview.stop;
						sf.stop;
					};

					// Audition the sound file if autoplay is checked
					if(autoPlay) { sfview.play };
				}.fork(AppClock);
			});


		});

		win.front.alwaysOnTop_(alwaysOnTop);
	}

	/*
	Convenience method for sample playback.

	This method can be used in musical applications (contrast against SampleFile's play method
	which should only be used for auditioning).

	The sample is played as a simple synth instance. This method returns a reference to
	the synth instance.
	*/


	*splay {|id, start=0, end=(-1), rate=1.0, amp=1.0, out=0, co=20000, rq=1, pan=0, loops=1, autogate=1|
		var dur, ch, syn, srate, smp = Smpl.samples.at(id);
		ch = smp.numChannels;
		if(end == -1) { end = smp.numFrames };
		if(ch==1) {
			syn = Synth(\smpl_pitchedSample1ch, [\amp, amp, \start, start, \end, end, \rootPitch, "A4".f,
				\freq, "A4".f * rate, \atk, 0.001, \rel, 0.1, \co, co, \rq, rq, \out, out, \pan, pan, \buf, smp.buffer, \loops, loops, \autogate, autogate]);
		} {
			syn = Synth(\smpl_pitchedSample2ch, [\amp, amp, \start, start, \end, end, \rootPitch, "A4".f,
				\freq, "A4".f * rate, \atk, 0.001, \rel, 0.1, \co, co, \rq, rq, \out, out, \pan, pan, \buf, smp.buffer, \loops, loops, \autogate, autogate]);
		};
		srate = smp.buffer.sampleRate;
		if(srate.isNil) { srate = smp.sampleRate };
		if(srate.isNil) { srate = activeServer.sampleRate };
		dur = ((end-start) / srate / rate) * loops;

		^syn;
	}

	// Convenience method, plays back a sample play spec using splay
	// a sample play spec is an array of the format
	// [sf.name, sf.numChannels, start, end]
	*splaySpec {|spec, rate=1.0, amp=1.0, out=0, co=20000, rq=1, pan=0, loops=1, autogate=1|
		Smpl.splay(spec[0], spec[2], spec[3], rate, amp, out, co, rq, pan, loops, autogate);
	}


	// Convenience method, insert the ids/paths of an array of sample ids
	*insertPathnamePairs {|samplelist|
		var rs = "";
		samplelist.do {|sm|
			rs = rs ++ "[\"%\",\"%\"],\n".format(sm, Smpl.at(sm).path);
		};
		rs = "[ \n% \n]".format(rs);
		Document.current.insertAtCursor(rs);
	}

}


/*
SampleFilePlayer {
var <positionBus=nil;
var <playNode=nil;
var <server=nil;
var <sf=nil;
var <sfview=nil;
var <triggerId;



/**** Sample Play Controls ****/
play {|out=0, startframe=0, endframe=(-1), amp=1.0, doneFunc|
var sdef;
this.stop;
if(endframe == -1 || (endframe > sf.buffer.numFrames)) { endframe = sf.buffer.numFrames };
if(sf.buffer.numChannels == 2) { sdef = sdef2ch } { sdef = sdef1ch };
playNode = Synth.new(sdef, [\out, out, \buf, sf.buffer, \pBus, positionBus,
\amp, amp, \start, startframe, \end, endframe]).onFree({ positionBus.setSynchronous(endframe); playNode = nil; if(doneFunc.notNil) { doneFunc.value } });
^this;
}
stop { if(playNode.notNil) { playNode.free; playNode = nil } }
/**** End Sample Play Controls ****/

}

*/


SampleFileView : CompositeView {
	classvar <dummyData;

	var <sf;
	var <oscfunc; // osc listener for cursor update messages
	var <sfview; // SoundFileView at the core of this view
	// other views
	var <playButton, <loopCheckbox;

	*initClass {
		dummyData = Array.fill(44100, {0.0});
	}

	*new {|samplefile, parent, bounds, guistyler|
		^super.new(parent, bounds).init(samplefile, guistyler);
	}

	// TODO: Use guistyler, if nil create a new one..
	init {|samplefile, guistyler|
		var playbuttonaction, mouseupaction, loopText;

		if(samplefile.isNil) { "Invalid SampleFile provided to SampleFileView: %".format(samplefile).throw };

		sf = samplefile;

		playButton = Button(this, Rect(0, this.bounds.height - 20, 100, 20)).states_([["Play"],["Stop"]]);

		loopCheckbox = CheckBox(this, Rect(120, this.bounds.height-20, 30, 30), "loop");

		loopText = StaticText(this, Rect(160, this.bounds.height-20, 100, 30)).string_("loop").action_({|txt| loopCheckbox.valueAction_(loopCheckbox.value.not) });

		loopCheckbox.action_({|cb|
			sf.loop_(cb.value);
			"looping %".format(cb.value).postln;
		});

		sfview = SoundFileView.new(this, Rect(0,0, this.bounds.width, this.bounds.height - 20));

		mouseupaction = {|sfv|
			var newpos, clickPos, regionLength;
			#clickPos, regionLength = sfv.selections[sfv.currentSelection];
			"Clicked % %".format(clickPos, regionLength).postln;
		};

		playbuttonaction = {|btn|
			if(btn.value==1) {// play
				var st, end, len, res;
				#st, len = sfview.selections[sfview.currentSelection];
				if(len == 0) { end = sf.numFrames } { end = st + len };
				if(st < sfview.timeCursorPosition) { st = sfview.timeCursorPosition };

				// 1. enable OSCFunc to recieve cursor position
				if(oscfunc.isNil) {
					oscfunc = OSCFunc.new({|msg|
						var id = msg[2], val = msg[3];
						{
							if(id == sf.triggerId) { // cursor position update
								sfview.timeCursorPosition = val;
							};
							if(id == (sf.triggerId+1)) { // done trigger
								var start, length;
								#start, length = sfview.selections[sfview.currentSelection];
								"done!".postln;
								// if looping is enabled then loop
								"looping is: % %".format(loopCheckbox, loopCheckbox.value).postln;
								if(loopCheckbox.value == true) {
									// loop
									"loop damnit".postln;
									// TODO: Is explicitly resetting the player necessary here?
									//playNode.set(\start, start, \end, start+length, \loop, 1, \t_reset, 1);
								} { // stop
									sf.stop();
									sfview.timeCursorPosition = st;
									playButton.value_(0);
								};

							};
						}.fork(AppClock);
					}, "/tr");
				};

				// 2. start a playback synth node (free previous if needed)
				//    TODO: ^^^ set a minimum duration limit so as not to overload the system
				res = sf.play(start: st, end: end, out: 0, amp: 1.0);
				if(res.isNil) { sf.stop };

			} {//stop
				sf.stop;
			};
		};


		if(sf.class === LiveSample) {
			"LOADING LIVESAMPLE INTO VIEW".warn;
			// Load the buffer data into an array
			sf.buffer.loadToFloatArray(action: {|data|
				{
					// TODO: This doesn't seem to work... :-/
					// probably need to break it down more fundamentally...
					// SoundFileView
					sfview.setData(data);//, samplerate: sf.sampleRate);

					sfview.timeCursorOn_(true)
					.timeCursorColor_(Color.white)
					.timeCursorPosition_(0);

					sfview.mouseUpAction_(mouseupaction);
					playButton.action_(playbuttonaction);

				}.fork(AppClock);
			});
		} {
			// Regular SampleFile can just read from disk
			"LOADING SAMPLEFILE INTO VIEW".warn;
			sfview.soundfile_(sf);
			sfview.read(0, sf.numFrames);
			sfview.timeCursorOn_(true).timeCursorColor_(Color.white).timeCursorPosition_(0);
			sfview.mouseUpAction_(mouseupaction);
			playButton.action_(playbuttonaction);
		};


		^this;
	}

	// Audition current selection
	play {
		playButton.valueAction_(1);
	}

	stop {
		playButton.valueAction_(0);
	}

	currentSelection {
		^sfview.selections[sfview.currentSelection];
	}

	getEventStringForCurrentSelection {
		var st, len, end;
		#st,len = sfview.selections[sfview.currentSelection];
		if(len==0) { end = -1 } { end = st + len };
		^sf.eventString(0.5, st, end);
	}

	getSampleSpecForCurrentSelection {
		var st, len, end;
		#st,len = sfview.selections[sfview.currentSelection];
		if(len==0) { end = sf.numFrames } { end = st + len };
		^[sf.name, sf.numChannels, st, end];
	}

}

// Representation of a live sample that can be used within Smpl Lib
LiveSample : SampleFile {
	classvar <recordDefName = \liveSampleRecord;
	classvar <recordLoopDefName = \liveSampleRecordLoop;
	classvar <liveSampleStatusListenerOSCdef = nil;
	classvar <liveSamplesById;
	classvar <>nextId = 1000;

	var <>listenerSynth;
	var <>listenerGroup;
	var <>server;
	var <id;
	var <duration;

	*initClass {
		liveSamplesById = Dictionary.new;
	}

	*new {|sname, buf, serv, group|
		^super.new.init(sname, buf, serv, group);
	}

	init {|sname, buf, serv, group|
		// TODO: in the future maybe want to enable option to write captured samples out to disk
		//path = "PATH/TO/WRITE/TO";
		//headerFormat = "WAV";
		id = LiveSample.nextId;
		LiveSample.nextId = LiveSample.nextId + 1000;
		server = serv;
		listenerGroup = group;
		name = sname;
		buffer = buf;
		numFrames = buf.numFrames;
		sampleRate = buf.sampleRate;
		numChannels = buf.numChannels;
		duration = numFrames / sampleRate;
		LiveSample.liveSamplesById.put(id, this);
		super.initMemberData(); // initialize SampleFile data fields
	}


	// listen type can be oneshot or loop
	pr_getNdefListenerFunc {|ndef, listenType=\oneshot|
		var code;
		switch(listenType,
			\oneshot, {
				code = "
{|buf, listenerId=1000, ampThresh=0.01, silenceTimeout=2, releaseAfter=20|
	var in, amp, timeElapsed, t_timeExpired;
	var t_trans, firstTrans, recActive, timeSinceTrans, t_recordingStop, t_reachedEnd;
	in = Ndef.ar('%');
	amp = Amplitude.kr(in, 0.01, 0.01);
	t_trans = Changed.kr(amp > ampThresh);
	firstTrans = Latch.kr(1, t_trans);
	timeSinceTrans = Sweep.kr(t_trans, 1.0);
	recActive = (amp > ampThresh) + ((timeSinceTrans < silenceTimeout) * firstTrans);
	t_recordingStop = (1-recActive) * firstTrans;

	t_reachedEnd = Done.kr(
		RecordBuf.ar(in, buf, 0, 1.0, run: recActive, loop: 0)
	);

	timeElapsed = Sweep.kr(1, 1.0);
	t_timeExpired = timeElapsed > releaseAfter;
	SendTrig.kr(DC.kr(1), listenerId + 1, 1);
	SendTrig.kr(t_timeExpired, listenerId + 2, 1);
	SendTrig.kr(recActive, listenerId + 3, 1);
	SendTrig.kr(t_recordingStop, listenerId + 4, 1);
	SendTrig.kr(t_reachedEnd, listenerId + 5, 1);
	FreeSelf.kr(t_timeExpired + t_reachedEnd + t_recordingStop);
}
".format(ndef.key);
			},
			\loop, {
				code = "
{|buf, listenerId=1000, releaseAfter=200|
    var insig, amp, timeElapsed, t_timeExpired, t_end;
	var firstTrans, recActive, timeSinceTrans, t_recordingStop, t_reachedEnd;
    insig = Ndef.ar('%');
	timeElapsed = Sweep.kr(1, 1.0);
	t_end = timeElapsed > releaseAfter;
	RecordBuf.ar(insig, buf, 0, 1.0, 0.0, 1, 1);
	SendTrig.kr(DC.kr(1), listenerId + 11, 1);
	SendTrig.kr(t_end, listenerId + 22, 1);
	FreeSelf.kr(t_end);
};
";
			},
			{ "Invalid listenType '%'".format(listenType).throw }
		);

		^code.interpret;
	}

	// in can be an input bus num or a ndef
	catch {|in=0, ampthresh=0.1, silenceThresh=1, timeout=10|
		if(listenerSynth.notNil) {
			listenerSynth.free;
			listenerSynth = nil;
		};

		// Launch a livesample synth to listen to signal source
		switch(in.class,
			Integer, { // use ready to go synthdef
				listenerSynth = Synth(recordDefName, [
					\buf, buffer,
					\listenerId, id,
					\inbus, in,
					\ampThresh, ampthresh,
					\silenceTimeout, silenceThresh,
					\releaseAfter, timeout
				], target: listenerGroup, addAction: \addToHead);
			},
			Ndef, { // create an anonymous synthdef based on recordDefName
				var fnc = this.pr_getNdefListenerFunc(in, \oneshot);
				listenerSynth = fnc.play(target: listenerGroup, outbus: 100, addAction: \addToHead, args: [\buf, buffer, \listenerId, id, \ampThresh, 0.1, \silenceTimeout, silenceThresh, \releaseAfter, timeout]);
			},
			{ "Invalid input source '%'".format(in).throw; }
		);

		^this;
		// liveSampleStatusListenerOSCdef
		// now controls responding to the status messages
		// if recording stops or the end of buffer is reached
		//     then free & nil the listenerSynth

	}

	// loop records to buffer until catchStop is called, or timeout is reached
	catchStart {|in=0, timeout=3000|
		if(listenerSynth.notNil) {
			listenerSynth.free;
			listenerSynth = nil;
		};

		// Launch a livesample synth to record indefinitely (until timeout)
		switch(in.class,
			Integer, { // use ready to go synthdef

				listenerSynth = Synth(recordLoopDefName, [
					\buf, buffer,
					\listenerId, id,
					\inbus, in,
					\releaseAfter, timeout
				], target: listenerGroup, addAction: \addToHead);
			},
			Ndef, { // create an anonymous synthdef based on recordDefName
				var fnc = this.pr_getNdefListenerFunc(in, \loop);
				listenerSynth = fnc.play(target: listenerGroup, outbus: 100, addAction: \addToHead, args: [\buf, buffer, \listenerId, id, \releaseAfter, timeout]);
			},
			{ "Invalid input source '%'".format(in).throw; }
		);

		// TODO:
		// liveSampleStatusListenerOSCdef
		// now controls responding to the status messages
		// if recording stops or the end of buffer is reached
		//     then free & nil the listenerSynth

		^this;
	}

	catchStop {
		if(listenerSynth.notNil) {
			listenerSynth.free;
			listenerSynth = nil;
		};
		^this;
	}



	// override
	loadFileIntoBuffer {|server, action=nil|
		if(action.notNil) {
			action.value(buffer);
		};
		^this;
	}


	*loadSynthDefs {
		var added = "";

		if(SynthDescLib.global.synthDescs.at(recordDefName).isNil) {
			added = added + " " + recordDefName;
			// listenerId must be evenly divisible by 1000
			SynthDef(recordDefName, {|buf, listenerId=1000, inbus=0, insig=0,
				ampThresh=0.01, silenceTimeout=2, releaseAfter=20|

				var in, amp, timeElapsed, t_timeExpired;
				var t_trans, firstTrans, recActive, timeSinceTrans, t_recordingStop, t_reachedEnd;
				in = SoundIn.ar(inbus) + insig;
				amp = Amplitude.kr(in, 0.01, 0.01);
				t_trans = Changed.kr(amp > ampThresh);
				firstTrans = Latch.kr(1, t_trans);
				timeSinceTrans = Sweep.kr(t_trans, 1.0);
				recActive = (amp > ampThresh) + ((timeSinceTrans < silenceTimeout) * firstTrans);
				t_recordingStop = (1-recActive) * firstTrans;

				t_reachedEnd = Done.kr(
					RecordBuf.ar(in, buf, 0, 1.0, run: recActive, loop: 0)
				);
				timeElapsed = Sweep.kr(1, 1.0);
				t_timeExpired = timeElapsed > releaseAfter;

				SendTrig.kr(DC.kr(1), listenerId + 1, 1);
				SendTrig.kr(t_timeExpired, listenerId + 2, 1);
				SendTrig.kr(recActive, listenerId + 3, 1);
				SendTrig.kr(t_recordingStop, listenerId + 4, 1);
				SendTrig.kr(t_reachedEnd, listenerId + 5, 1);

				FreeSelf.kr(t_timeExpired + t_reachedEnd + t_recordingStop);
			}).add;
		};



		if(SynthDescLib.global.synthDescs.at(recordLoopDefName).isNil) {
			added = added + " " + recordLoopDefName;
			SynthDef(recordLoopDefName, {|buf, in=0, ndefin=0, listenerId=1000, releaseAfter=30|
				var insig, amp, timeElapsed, t_timeExpired;
				var t_end;
				var firstTrans, recActive, timeSinceTrans, t_recordingStop, t_reachedEnd;

				//insig = Select.ar(ndefin, [SoundIn.ar(in), Ndef.ar(in)]);

				insig = SoundIn.ar(in);

				timeElapsed = Sweep.kr(1, 1.0);
				t_end = timeElapsed > releaseAfter;
				RecordBuf.ar(insig, buf, 0, 1.0, 0.0, 1, 1);

				SendTrig.kr(DC.kr(1), listenerId + 11, 1);
				SendTrig.kr(t_end, listenerId + 22, 1);

				FreeSelf.kr(t_end);
			}).add;

		};

		if(added != "") {
			"Smpl: Adding synthdefs: %".format(added).warn;
		};



		if(liveSampleStatusListenerOSCdef.isNil) {
			liveSampleStatusListenerOSCdef = OSCdef(\liveSampleStatusListener, {|msg|
				var ls, listenerId, statusId;
				listenerId = msg[2].asInteger;
				statusId = listenerId % 1000;
				listenerId = (listenerId / 1000).asInteger * 1000;
				ls = liveSamplesById.at(listenerId);
				//"% listener: %   status: %".format(ls, listenerId, statusId).postln;
				if(ls.notNil) {
					switch( statusId,
						1, {
							"Waiting for audio % %".format(listenerId, ls.name).postln;
						},
						2, {
							"Time Expired liveSample % %".format(listenerId, ls.name).warn;
							ls.listenerSynth = nil;

						},
						3, {
							"Recording Started % %".format(listenerId, ls.name).postln;

						},
						4, {
							"Recording Stopped % %".format(listenerId, ls.name).postln;
							ls.listenerSynth = nil;
						},
						5, {
							"Reached End of Buffer % %".format(listenerId, ls.name).warn;
							ls.listenerSynth = nil;
						},
						11, {
							"Started Looped Recording % %".format(listenerId, ls.name).warn;
						},
						22, {
							"Stopped Looped Recording % %".format(listenerId, ls.name).warn;
							ls.listenerSynth = nil;
						},
					);
				};
			}, "/tr");
		};
	}

	asString {
		^"LiveSample % %".format(id, name);
	}

}





// Extension of SoundFile with playback and metadata additions for working within Smpl library
SampleFile : SoundFile {

	// Smpl Plumbing
	classvar rootFolder = "_samples";

	// ** Playback **
	var buffer;      // buffer, if loaded, on the server where this soundfile exists

	// ** Metadata **
	var <>name, <tags, <>library, <folderGroups;  // belongs to a sample library, name of library
	var <>rootPitch;

	// FUTURE:: fancier analysis-related things
	var frequency_profile;
	var average_tempo;
	var average_pitch;
	var markers; // time markers


	// Playback PLUMBING
	classvar <playdef1ch = \sfPlay1;
	classvar <playdef2ch = \sfPlay2;
	classvar <cursordef1ch = \SampleFileCursorPos1Channel;
	classvar <cursordef2ch = \SampleFileCursorPos2Channel;

	var <playNode;    // synth node controlling buffer playback
	var <loop = false; // boolean: loop playback


	// GUI plumbing
	classvar <nextTriggerId=111;
	var <triggerId;

	// GUI views
	var waveformview=nil; // main composite view

	cleanup {
		this.stop;
		waveformview = nil;
	}

	/*
	@Override
	@path A PathName
	NOTE: This needs to be redone to return an instance of SampleFile, I might actually need to rewrite SoundFile
	*/
	*openRead {|path, rootPitch| if(this.isSoundFile(path)) { ^super.new.init(path, rootPitch) } { ^nil } }

	// path is of type PathName
	init {|path, rootpitch|

		this.initMemberData();
		// Filepath initialization
		this.openRead(path.asAbsolutePath); // get metadata
		this.close; // close file resource
		folderGroups = this.pr_getFolderGroups(path);
		// TODO: Should load tags and other info from external metadata file here
		name = path.fileNameWithoutExtension.replace(" ","");

	}

	initMemberData {|rootpitch|
		var rx;
		triggerId = nextTriggerId;
		nextTriggerId = nextTriggerId + 111;
		tags = Set.new;

		if(rootpitch.isNil) {
			// Get root pitch from filename
			if(name.size > 3) {
				rx = name[name.size-3..].findRegexp("([a-g][sb]?[0-9])");
				if(rx.size > 0) {
					rootpitch = rx[0][1].asSymbol;
				};
			};
		};
		if(rootpitch.isNil) {
			rootpitch = \c5;
		};
		rootPitch = rootpitch;
	}


	// path is of type PathName
	pr_getFolderGroups {|path|
		var dirnames, rootpos;
		dirnames = path.pathOnly.split($/);
		rootpos = dirnames.find([rootFolder]);
		if(rootpos.isNil) {
			^nil;
		} {
			^dirnames[(rootpos+1)..]; // everything after the root directory
		};

	}


	// Get a waveform view attached to this samplefile
	getWaveformView {|parent, bounds|
		var loopText;
		if(waveformview.notNil) { ^waveformview };
		if(bounds.isKindOf(Point)) { bounds = Rect(0,0,bounds.x,bounds.y) };
		waveformview = SampleFileView.new(this, parent, bounds);
		^waveformview;
	}


	/*
	Play method ONLY FOR AUDITIONING or in tandem with SampleFileView

	This method should not be used in patterns or other timed musical applications.
	This method keeps only a single synth instance active and resets the playback position
	as needed. It works in tandem with SampleFileView to maintain cursor position and looping behavior.

	For musical applications use the `event` method to get an independent playable event.
	*/
	play {|server=nil, start=0, end=(-1), out=0, amp=1.0|
		var syn = if(numChannels == 2) { cursordef2ch } { cursordef1ch };
		if(server.isNil) { server = Server.default };
		this.pr_checkServerAlive(server, {^nil});
		this.pr_checkBufferLoaded({^nil});
		if(end == -1) { end = this.numFrames };
		if(end-start > 1000) {
			if(playNode.notNil) { playNode.free; playNode = nil };
			playNode = Synth(syn, [\buf, buffer, \out, out, \amp, amp, \start, start, \end, end, \tid, triggerId, \loop, loop]);
			^this;
		} {
			^nil;
		};
	}

	stop {
		if(playNode.notNil) { playNode.free; playNode = nil };
	}

	loop_ {|bool=false|
		loop = bool;
		if(playNode.notNil) { playNode.set(\loop, loop.asInteger) };
	}


	*loadSynthDefs {
		if(SynthDescLib.global.synthDescs.at(playdef1ch).isNil) {
			SynthDef(playdef1ch, {|amp, out, start, end, buf|
				var sig, head;
				head = Line.ar(start, end, ((end-start) / (SampleRate.ir * BufRateScale.kr(buf))), doneAction: 2);
				sig = BufRd.ar(1, buf, head, 0);
				Out.ar(out, sig * amp);
			}).add;
		};

		if(SynthDescLib.global.synthDescs.at(playdef2ch).isNil) {
			SynthDef(playdef2ch, {|amp, out, start, end, buf|
				var sig, head;
				head = Line.ar(start, end, ((end-start) / (SampleRate.ir * BufRateScale.kr(buf))), doneAction: 2);
				sig = BufRd.ar(2, buf, head, 0);
				Out.ar(out, sig * amp);
			}).add;
		};

		if(SynthDescLib.global.synthDescs.at(cursordef1ch).isNil) {
			SynthDef(cursordef1ch, {|amp=0.5, out=0, tid, start=0, end, loop=1, buf, cursorRate=20|
				var sig, pos, dur, env, t_done, reset;
				var controlBlockFrames = SampleRate.ir / ControlRate.ir;
				reset = \t_reset.tr(1);
				dur = (end-start) / (SampleRate.ir * BufRateScale.kr(buf));
				pos = Phasor.ar(reset, 1 * BufRateScale.kr(buf), start, end, start);
				sig = BufRd.ar(1, buf, pos, 0);
				t_done = T2K.kr(pos >= (end-controlBlockFrames));
				SendTrig.kr(Impulse.kr(cursorRate) * (1 - t_done), tid, pos); // cursor position
				SendTrig.kr(t_done, tid+1, 1.0); // done signal
				FreeSelf.kr(t_done * (1-loop));
				Out.ar(out, sig * amp);
			}).add;
		};

		if(SynthDescLib.global.synthDescs.at(cursordef2ch).isNil) {
			SynthDef(cursordef2ch, {|amp=0.5, out=0, tid, start=0, end, loop=1, buf, cursorRate=20|
				var sig, pos, dur, env, t_done, reset;
				var controlBlockFrames = SampleRate.ir / ControlRate.ir;
				reset = \t_reset.tr(1);
				dur = (end-start) / (SampleRate.ir * BufRateScale.kr(buf));
				pos = Phasor.ar(reset, 1 * BufRateScale.kr(buf), start, end, start);
				sig = BufRd.ar(2, buf, pos, 0);
				t_done = T2K.kr(pos >= (end-controlBlockFrames));
				SendTrig.kr(Impulse.kr(cursorRate) * (1 - t_done), tid, pos); // cursor position
				SendTrig.kr(t_done, tid+1, 1.0); // done signal
				FreeSelf.kr(t_done * (1-loop));
				Out.ar(out, sig * amp);
			}).add;
		};

	}


	// utility function used when an active server is needed
	pr_checkServerAlive {|server, errorFunc|
		if(server.serverRunning.not) {
			error("Cannot complete operation: Boot the Server First!");
			errorFunc.value;
		};
	}
	pr_checkBufferLoaded {|errorFunc|
		if(buffer.isNil) {
			error("Cannot complete operation: Load buffer into memory first!");
			errorFunc.value;
		};
	}

	buffer { this.pr_checkBufferLoaded({^nil}); ^buffer }


	addTag {|tag| tags.add(tag) }
	hasTag {|tag| if(tag.isNil) { ^true } { ^tags.contains(tag) } }

	*isSoundFile {|path|
		if(path.class != PathName) { path = PathName.new(path) };
		^"^(wav[e]?|aif[f]?)$".matchRegexp(path.extension.toLower);
	}

	/*
	Return a playback event that can be used in musical applications.
	*/
	event {|amp=0.5, startframe=0, endframe=(-1)|
		var sdef;
		this.pr_checkBufferLoaded({^nil});
		sdef = if(this.numChannels == 2) { playdef2ch } { playdef1ch };
		if(endframe == -1) { endframe = this.numFrames };
		^(type: \bufPlay, amp: amp, start: startframe, end: endframe, buf: buffer.bufnum);
	}

	eventString {|amp=0.5, startframe=0, endframe=(-1)|
		var sdef;
		this.pr_checkBufferLoaded({^nil});
		sdef = if(this.numChannels == 2) { playdef2ch } { playdef1ch };
		if(endframe == -1) { endframe = this.numFrames };
		^"(type: 'smpl', amp: %, start: %, end: %, smpl: \"%\", loops: 1)".format(amp, startframe, endframe, this.name);
	}

	// Get stereo buffer as mono buffer
	// @param mixmode Mix stereo buffer down to mono \left, \right, \mix
	asMonoBuffer {|server, mixmode=\mix, doneAction=nil|
		this.pr_checkBufferLoaded({^nil});
		"Buffer Loaded: CHECK".postln;
		if(buffer.numChannels == 2) {
			var monobuf = Buffer.alloc(server, buffer.numFrames, 1);
			"Stereo buf, mono buffer allocated".postln;
			buffer.normalize.loadToFloatArray(action: {|arr|
				var monomix;
				"Loading into float array...".postln;
				monomix = Array.newClear(arr.size / 2).collect {|it, idx|
					switch(mixmode,
						\right, { arr[idx*2] },
						\left, { arr[idx*2 + 1] },
						\mix, { arr[idx*2] + arr[idx*2 + 1] },
						{ Error("Invalid mixdown option % when loading sample %".format(monomix, this)).throw; }
					);
				};
				"Array collected and buf is %".format(monobuf).postln;
				monobuf.loadCollection(monomix.normalize(-1,1), 0, { "Loaded mono buf".postln });
				"Success!".postln;
			});
			^monobuf;
		} {
			^buffer;
		};
	}


	/*
	loadFileIntoBuffer
	@param server The server to load this buffer into
	@param action Function to be evaluated once the file has been loaded into the server. Function is passed the buffer as an argument.
	*/
	loadFileIntoBuffer {|server, action=nil|
		var newbuf, newaction;
		server = server ? Server.default;
		this.pr_checkServerAlive(server, { ^nil });
		SampleFile.loadSynthDefs; // TODO: maybe not needed, should be done in Smpl
		newaction = action;
		if(buffer.notNil) {
			// buffer exists, run action but do not allocate
			"Buffer % already exists".format(buffer).warn;
			newaction.value(buffer);
		} {
			newbuf = Buffer.read(server, this.path, action: newaction);
			buffer = newbuf;
		};
		^this;
	}

	// Convenience method for loadFileIntoBuffer
	load {|server, action| this.loadFileIntoBuffer(server, action) }

	isLoaded {|server|
		server = server ? Server.default;
		this.pr_checkServerAlive(server, {^false});
		^buffer.notNil
	}

	bufnum { ^buffer.bufnum }
	asString { ^("SampleFile"+path) }

}



