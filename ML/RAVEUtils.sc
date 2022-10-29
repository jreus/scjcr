/*
RAVE modelmanager provides a flexible instrument
for controlling multiple RAVE models in live performance.

NOTE: At the moment only supports one RAVE synth at a time..
TODO: Expand to allow managing multiple RAVE models and morphing between them.
See: Jennifer Hsu, "it's morphin time"
*/

RAVEModelManager {

	var <server;
	var <ins, <outs; // input and output busses, used to pipe audio to and from the RAVE models
	var <targetGroup; // group where RAVE synths will sit, ideally this should go after input UGens and before FX and master synths
	var <targetModel = nil; // currently selected RAVE synth, all control operations target this synth
	var <>mscale = 1.0; // scaling factor for latent biases, values >1.0 start to saturate
	var <availableModels; // all available RAVE models

	var <>modelsRoot;
	var <models;


	*new {|serv, target|
		^super.new.init(serv);
	}

	init {|serv, target|
		if(serv.isNil) {
			serv = Server.default;
		};
		server = serv;

		if(server.serverRunning.not) {
			"Cannot initialize RAVEModelManager - boot server first!".throw;

		};

		if(target.isNil) {
			target = Group.new(server, \addAfter);
		};
		targetGroup = target;
		ins = 3.collect {|idx| Bus.audio(server, 2) };
		outs = 3.collect {|idx| Bus.audio(server, 2) };
	}

	/*
	Parse a modelspec Dictionary that contains information about RAVE torchscript models

	rootpath <str> root path to RAVE models
	modelspec <Dictionary> dictionary of model id->modelspec
	jsonfile <filepath> alternatively 'root' and 'models' can be specified in a json file

	Each modelspec entry must have the following parameters:
	  path  <str> path to model file, minus the rootpath
	  numLatents  <int> number of latents in the ts file
	  gate  <bool> add a noisegate or not
	  gateThresh <float> volume thresh for the gate to kick in
	*/
	parseModels {|rootpath, modelspec, jsonfile|
		var modelerror = false;

		if(jsonfile.notNil) { // Parse JSON
			var json = jsonfile.parseJSONFile;
			modelsRoot = json["root"];
			models = Dictionary.new;
			json["models"].keysValuesDo {|id, modelspec|
				models[id.asSymbol] = (
					path: modelspec["path"],
					numLatents: modelspec["numLatents"],
					gate: modelspec["gate"],
					gateThresh: modelspec["gateThresh"]
				);
			};
		} { // Use input arguments
			modelsRoot = rootpath;
			models = modelspec;
		};

		"RAVEModelManager. ...scanning RAVE models...".postln;
		models.keysValuesDo {|id, path|
			if( this.getModelPath(id).isFile ) {
				"FOUND MODEL: %".format(id).postln;
			} {
				"COULD NOT FIND MODEL FILE: %".format(id).error;
				modelerror=true;
			};
		};

		if(modelerror) {
			"One or more RAVE models could not be found!".throw;
		};

		"RAVEModelManager. ...generating synth functions for all models...".postln;
		models.keys.do {|id|
			var model = models[id];
			"Parsing % ...".format(id, model).postln;
			// Add synth param and makeSynth function
			model[\synth] = nil;
			model[\makeSynth] = {|inbus, outbus, targetGroup, addAction|
				"Create RAVE % with % % % %".format(id, inbus, outbus, targetGroup, addAction).postln;
				model[\synth].free; // free preexisting synth
				model[\synth] = {|encoder=1.0, gain=1.0| // create RAVE synth..
					var insig, outsig, z;
					var zscale = model[\numLatents].collect{ |i| ("ls"++i).asSymbol.kr(1.0) };
					var zbias = model[\numLatents].collect{ |i| ("lb"++i).asSymbol.kr(0.0) };
					insig = In.ar(inbus);
					z = RAVEEncoder.new( this.getRawModelPath(id), model[\numLatents], insig) * encoder;
					z = (z * zscale) + zbias;
					outsig = RAVEDecoder.new( this.getRawModelPath(id), z );
					if(model[\gate]) {
						outsig = Compander.ar(outsig, outsig, model[\gateThresh], 2.0, 1.0, 0.01, 0.01);
					};
					outsig * gain;
				}.play(target: targetGroup, outbus: outbus, addAction: addAction);
				model[\synth];
			};
		};

	}

	// Get the path to a given model as a PathName
	getModelPath {|id|
		^PathName( modelsRoot +/+ models[id].path )
	}

	// Get the path to a given model as a pathstring
	getRawModelPath {|id|
		^this.getModelPath(id).fullPath
	}

	// Set name,value pairs on the currently selected RAVE synth
	setParam {|... args|
		postln("Set ((%)) on %".format(args, targetModel));
		targetModel.set(*args);
		^targetModel;
	}

	listModels {
		models.keys.postln;
		^models.keys;
	}

	/*
	Activates a model and makes it the target of all control operations.
	id            model id
	inbus         audio bus to use as input to the RAVE synth, if nil us ins[0]
	outbus        audio bus to use as output from the RAVE synth, if nil use outs[0]
	target        server group to put the RAVE synth, if nil use default targetGroup
	*/
	selectModel {|id, inbus, outbus, target|
		var model = models[id];
		if(inbus.isNil) { inbus = ins[0] };
		if(outbus.isNil) { outbus = outs[0] };
		if(model.notNil) {
			"Selecting Model: %".format(id).postln;
			targetModel.free; // TODO: Maybe I don't want to do this? Should I allow multiple RAVE synths simultaneously?
			if(target.isNil) { target = targetGroup };
			model[\makeSynth].value(inbus, outbus, target, \addToTail);
			targetModel = model[\synth];
		} {
			"Unknown model '%'".format(id).warn;
		};
	}

	// Clear all RAVE synths and clean up resources...
	clearAll {
		// For now it's just freeing the target synth..
		targetModel.free;
	}

}



