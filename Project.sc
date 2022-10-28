/******************************************
General SC Project Class for workflow

(C) 2019 Jonathan Reus

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

https://www.gnu.org/licenses/

_____________________________________________________________
Project
A project system for livecoding and general SuperCollider projects
that wraps Scenes, Macros, Smpl, Syn and other major workflow modules.

@usage
Create a file called _main.scd inside your project root folder.
Subdirectories for things like macros, scenes, and samples will be
greated within when you initialize the project for the first time.

Inside _main.scd call

~sc = Scenes.new;

f = "".resolveRelative +/+ "scenes";
z = Scenes(f).makeGui;
________________________________________________________________*/

Project {
	classvar <>defaultSynthPath;
	classvar <>samplerates;
	classvar <>blocksizes;

	classvar <>devicepreference;
	classvar <meters, <scenesWin;
	classvar <audioChain;
	classvar <reaperBridge;
	classvar <>memSize, <>numBuffers, <>numOutputs, <>numInputs;

	classvar <>limitSamplesLocal=1000, <>limitSamplesGlobal=1000;
	classvar <garbage;
	classvar <>resetAction;

	// audio chain
	classvar <>outhw = 0;
	classvar <>outclean=10, <>outverb=12, <>outbass=14; // hardware shared busses
	classvar <mixersynth;

	// TODO: create a project preferences file to include this stuff...
	*initClass {
		samplerates = [11025, 22050, 44100, 48000, 88200, 96000, 1920000];
		blocksizes = [16,32,64,128,256,512,1024, 2048, 4096];
		memSize = 2.pow(18);
		numBuffers = 2.pow(10);
		numOutputs = 2;
		numInputs = 2;
		garbage = List.new;
		resetAction = {
			"Project reset".warn;
			MPK.initMidiFuncs;
		};
		defaultSynthPath = "~/Dev/SC_Libs/scjcr/SynthDefs/".absolutePath;
	}

	*meter {|server|
		var bnd, len;
		server = server ? Server.default;
		if(meters.isNil.or { meters.window.isClosed }) {
			meters = server.meter;
			meters.window.alwaysOnTop_(true).front;
			bnd = meters.window.bounds;
			len = Window.screenBounds.width - bnd.width;
			meters.window.bounds = Rect(len, 0, bnd.width, bnd.height);
		} {
			meters.window.front;
		};
		^meters;
	}

	*scenes {
		if(scenesWin.isNil.or { scenesWin.isClosed }) {
			scenesWin = Scenes.gui;
			scenesWin.alwaysOnTop_(true).front;
		} {
			scenesWin.front;
		};
		^scenesWin;
	}

	// Use this every time a temporary process; synth, ndef, or pdef is created
	*collect {|thesynth|
		garbage.add(thesynth);
	}

	// clean up all synths & other extraneous processes
	*cleanup {
		garbage.do {|proc|
			if(proc.notNil) {
				if(proc.isKindOf(Synth)) {
					proc.free;
				};
				if(proc.isKindOf(Ndef)) {
					proc.clear;
				};
				if(proc.isKindOf(Pdef)) {
					proc.clear;
				};
				if(proc.isKindOf(Tdef)) {
					proc.stop;
				};
			};
		};
		garbage = List.new;
	}

	// hard reset, when in need of a hard reboot
	*reset {
		this.resetAction.();
	}

	// TODO: I think reaperbridge  and the audiochain might be rotten code at this point..
	// TODO: Probably we want to integrate FX and Beat somehow?
	// TODO: Is Macros also coderotten?
	*boot {|server=nil, sampleRate=44100, blockSize=512, hardwareBufferSize=512, verbose=false, scenes=false, meters=false, macros=false, slimLocal, slimGlobal, mixer=false, reaper=false, synthPath=nil, rootPath=nil, localSamplePaths=nil, onBoot=nil|

		if(slimLocal.notNil) {
			this.limitSamplesLocal = slimLocal;
		};
		if(slimGlobal.notNil) {
			this.limitSamplesGlobal = slimGlobal;
		};

		/* TODO: Are these code-rotten?
		if(mixer.notNil) { audioChain = mixer };
		if(reaper.notNil) { reaperBridge = reaper };
		*/

		if(rootPath.isNil) { "`rootPath` must specify a valid project root directory in Project.startup".throw };

		if(server.isNil) { server = Server.default };
		server.options.sampleRate = sampleRate;
		server.options.blockSize = blockSize;
		server.options.hardwareBufferSize = hardwareBufferSize;
		server.options.numOutputBusChannels = numOutputs;
		server.options.numInputBusChannels = numInputs;

		// Init non-server dependent modules
		Scenes.init(rootPath, rootPath +/+ "_scenes/");
		Macros.load(rootPath +/+ "_macros/");
		if(macros == true) { Macros.preprocessor = true };
		if(localSamplePaths.isNil) { // use default project structure local samples directory
			localSamplePaths = [rootPath +/+ "_samples/"];
			if(File.exists(localSamplePaths[0]).not) { File.mkdir(localSamplePaths[0]) };
		};
		Project.pr_makeStartupWindow(server, localSamplePaths, synthPath, onBoot, meters, scenes, verbose);
	}


	*pr_makeStartupWindow {|server, localSamplePaths, synthPath, onBoot, showMeters, showScenes, verbose|
		var win, styler, container, subview;
		var srdropdown, bsdropdown, devlist, yesbut, nobut;
		var showscenesBut, showmetersBut, allsamples;

		if(synthPath.isNil) {
			synthPath = defaultSynthPath;
		};

		win = Window.new("Choose Audio Device", Rect(Window.screenBounds.width / 2.5, Window.screenBounds.height / 2.4,300,200), false);
		styler = GUIStyler(win);
		container = styler.getView("Start", win.view.bounds, margin: 10@10, gap: 10@30);
		styler.getSizableText(container, "samplerate", 55, \right);
		srdropdown = PopUpMenu(container, 70@20).items_(samplerates).value_(samplerates.indexOf(server.options.sampleRate));

		styler.getSizableText(container, "blocksize", 60, \right);
		bsdropdown = PopUpMenu(container, 60@20).items_(blocksizes).value_(blocksizes.indexOf(server.options.blockSize));

		styler.getSizableText(container, "device", 55, \right, 14);
		devlist = PopUpMenu.new(container, Rect(0, 0, 210, 30)).font_(Font("Arial", 16));
		Platform.case(
		\linux, {
				"Loading audio device options for LINUX".warn;
				devlist.items_(["JACK"]);
			},
		\osx, {
				"Loading audio device options for OSX".warn;
				devlist.items_(ServerOptions.devices.sort);
			},
		\windows, {
				"Loading audio device options for Windows".warn;
				devlist.items_(ServerOptions.devices.sort);
			},
		);

		/* TODO: I don't think these buttons actually do anything meaningful anymore....

		subview = styler.getView("AudioChain", 50@70, false, 0@0, 0@0, container);
		audiochain = RadioButton(subview, 50@50, \box);
		audiochain.value = audioChain;
		styler.getSizableText(subview, "Audio Chain", 50, \center);

		subview = styler.getView("ReaperBridge", 50@70, false, 0@0, 0@0, container);
		reaperbridge = RadioButton(subview, 50@50, \box);
		reaperbridge.value = reaperBridge;
		styler.getSizableText(subview, "Reaper Bridge", 50, \center);

		*/


		// Show Scenes
		subview = styler.getView("ShowScenes", 50@70, false, 0@0, 0@0, container);
		showscenesBut = RadioButton(subview, 50@50, \box);
		showscenesBut.value = showScenes;
		styler.getSizableText(subview, "Show Scenes", 50, \center);

		// Show Meters
		subview = styler.getView("ShowMeters", 50@70, false, 0@0, 0@0, container);
		showmetersBut = RadioButton(subview, 50@50, \box);
		showmetersBut.value = showMeters;
		styler.getSizableText(subview, "Show Meters", 50, \center);

		// Load Samples Limits
		subview = styler.getView("Load all Samples", 50@70, false, 0@0, 0@0, container);
		allsamples = RadioButton(subview, 50@50, \box);
		styler.getSizableText(subview, "Load All Samples?", 50, \center);

		yesbut = styler.getSizableButton(container, "Start", size: 85@70).font_(Font("Arial", 18));
		nobut = styler.getSizableButton(container, "Cancel", size: 60@70).font_(Font("Arial", 14));

		yesbut.action_({|btn|
			Platform.case(\linux, {
				"USING JACK".warn;
			}, {
				// OSX / WINDOWS
				server.options.device = devlist.items[devlist.value];
			});
			server.options.numInputBusChannels = numInputs;
			server.options.numOutputBusChannels = numOutputs;
			server.options.memSize = memSize;
			server.options.blockSize = bsdropdown.item.asInteger;
			server.options.sampleRate = srdropdown.item.asInteger;
			server.options.numWireBufs = 512 * 2;
			server.options.numBuffers = numBuffers;
			win.close;
			"BOOT: % %  %".format(
				srdropdown.item.asInteger, bsdropdown.item.asInteger,
				devlist.items[devlist.value]
			).warn;
			server.waitForBoot { // load server-dependent modules

				// Load Synthdefs...
				"LOADING SYNTH LIBRARY AT %".format(synthPath).warn;
				Syn.load(synthPath);
				if(allsamples.value == true) {
					this.limitSamplesGlobal = 50000;
					this.limitSamplesLocal = 50000;
				};

				// Show scenes & meters
				if(showmetersBut.value) { this.meter };
				if(showscenesBut.value) { this.scenes };

				// Load Samples ...
				Smpl.load(server, verbose: verbose, limitLocal: this.limitSamplesLocal, limitGlobal: this.limitSamplesGlobal, localSamplePaths: localSamplePaths, doneFunc: {

					/*
					TODO: I think these two lines might be rotten code...
					if(audiochain.value) { this.initAudioChain(server) };
					if(reaperbridge.value) { Rea.init };
					*/



					if(onBoot.notNil) { onBoot.value };
				});
			};
		});
		nobut.action_({|btn| win.close});
		win.alwaysOnTop_(true).front;
		win.view.keyDownAction = { |doc, char, mod, unicode, keycode, key|
			if(unicode == 13) { yesbut.doAction() };
		};
	}

	*initAudioChain {|serv|
		SynthDef(\projectMixer, {|master=1.0, verbsize=0.7, verbdamp=0.8, verbmix=0.2, bassco=500, bassrq=0.5, bus_clean, bus_verb, bus_bass, mainout|
			var sig, inverb, inclean, inbass;
			inverb = InFeedback.ar(bus_verb, 2);
			inclean = InFeedback.ar(bus_clean, 2);
			inbass = InFeedback.ar(bus_bass, 2);
			inverb = FreeVerb.ar(inverb, verbsize, verbdamp, verbmix);
			inbass = BLowPass4.ar(inbass, bassco, bassrq);
			sig = inverb + inclean + inbass;
			sig = Limiter.ar(LeakDC.ar(sig * master), 1.0, 0.001);
			Out.ar(mainout, sig);
		}).add;
		mixersynth = Synth(\projectMixer, [\bus_clean, outclean, \bus_verb, outverb, \bus_bass, outbass, \mainout, outhw], target: serv, addAction: \addToHead);
		^mixersynth;
	}
}