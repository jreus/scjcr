/******************************************

Buffer instrument. Uses a single continuous mono buffer.

(C) 2019 Jonathan Reus
liscensed according to the GPLv3

jonathanreus.com
info@jonathanreus.com

Inspired by Frank Baldé's LiSa
https://monoskop.org/Frank_Bald%C3%A9

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


*******************************************/





/* USAGE: *********************

l = Lisa.new(s, 60); // initialize buffer size

( // Loading & Sampling without zones
l.loadSample("LifeIsLike-Choir", 0, 0, 0);
l.liveSample(0, 5, 1000000);
);
l.show;

( // Using the zone system
l.initNumZones(10);


// Load samples into those zones
~preload.do {|smp, idx|
  "PRELOAD: % %".format(smp, idx).postln;
  if(idx < 10) {
    l.loadSampleToZone(smp, 0, 0, idx, true);
  };
};

);

l.show;
l.playZone(9, 0, 1.0);
l.liveSampleToZone(0, 2, true, 0);
l.playZone(2, 0, 1.0)



********************************/


Lisa {
  classvar <max_zones = 24;
  var <server;
  var <dur, <numFrames, <bigbuf;
  var <zones, <selectedZone = 0;

  var <win, <zoneText;

  *new {|serv, dur|
    ^super.new.init(serv, dur);
  }

  init {|serv, length|
    if(serv.isNil) { server = Server.default } { server = serv };
    this.pr_checkServerRunning({^nil});
    this.pr_loadSynthdefs;
    dur= length;
    numFrames = dur * server.sampleRate;
    bigbuf = Buffer.alloc(server, numFrames, 1);
    zones = Array.newClear(max_zones);
  }

  initZones {|zonesarr|
    zonesarr.do {|it,idx|
      zones[idx] = it;
    };
  }

  initNumZones {|numzones|
    var zonesize;
    if(numzones > max_zones) { numzones = max_zones };
    zonesize = numFrames / numzones;
    (numzones).do {|it|
      zones[it] = [it * zonesize, (it+1) * zonesize];
    };
  }

  zone {|idx| ^zones[idx] }

  zone_ {|idx, zone| zones[idx] = zone }

  playZone {|zoneidx, out=0, amp=1.0|
    var st, end, zn = zones[zoneidx];
    st = zn[0];
    end = zn[1];
    ^Synth(\simplePlayBuf, [\out, out, \amp, amp, \buf, bigbuf, \start, st, \end, end]);
  }

  pr_checkServerRunning {|errorFunc|
    if(server.serverRunning.not) {
      "Cannot Complete Operation! BOOT SERVER FIRST".error;
      errorFunc.value();
    }
  }

  pr_loadSynthdefs {
    // Sample a Duration of Time into a buffer
    SynthDef(\sampleDur, {|inbus, buf, dur=1, insertAt=0, mix=0.0, preamp=1.0, limit=0|
      var insig, run = Line.kr(0,1, dur, doneAction: 2);
      insig = SoundIn.ar(inbus) * preamp;
      insig = Select.ar(limit, [insig, Limiter.ar(insig, 1.0, 0.001)]);
      RecordBuf.ar(insig, buf, insertAt, 1.0-mix, mix, 1.0, 0);
    }).add;

    // Sample a Number of Frames into a buffer
    SynthDef(\sampleFrames, {|inbus, buf, insertAt, numframes, preamp=1.0, limit=0|
      var insig, head, dur;
      insig = SoundIn.ar(inbus) * preamp;
      insig = Select.ar(limit, [insig, Limiter.ar(insig, 1.0, 0.001)]);
      dur = numframes / (SampleRate.ir * BufRateScale.kr(buf));
      head = Line.ar(insertAt, insertAt+numframes, dur, doneAction: 2);
      BufWr.ar(insig, buf, head, 0.0);
    }).add;

    // Simple playback
    SynthDef(\simplePlayBuf, {|out=0, buf, amp=1.0, pan=0, start, end, rate=1, atk=0.01, rel=0.01|
      var sig, head, dur;
      dur = (end-start) / (SampleRate.ir * BufRateScale.kr(buf)) / rate;
      head = Line.ar(start, end, dur);
      sig = BufRd.ar(1, buf, head, 0) * EnvGen.ar(Env.linen(atk, dur-atk-rel, rel), 1, doneAction: 2);
      Out.ar(out, Pan2.ar(sig, pan, amp));
    }).add;
  }

  loadSample {|smpl, srcst=0, numframes=0, insertAt=0|
    var smp = Smpl.at(smpl);

    if(numframes <= 0) { numframes = smp.numFrames - srcst };
    if((numframes + insertAt) > bigbuf.numFrames) {
      numframes = bigbuf.numFrames - insertAt;
    };
    smp.buffer.copyData(bigbuf, insertAt, srcst, numframes);
  }

  liveSample {|inbus=0, dur=1, insertAt=0, limit=0|
    ^Synth(\sampleDur, [
      \inbus, inbus, \buf, bigbuf,
      \dur, dur, \insertAt, insertAt, \mix, 0.0, \preamp, 1.0, \limit, 0
    ]);
  }


  // copy sample into zone
  loadSampleToZone {|srcsmpl, srcst, nframes, destzoneidx, zeroFirst=false|
    var zonesize, destzone, srcbuf = Smpl.at(srcsmpl).buffer;

    if(srcbuf.numChannels > 1) {
      "LiSa: Cannot load stereo sample file '%'. Mix to mono first!".format(srcsmpl).error;
      ^nil;
    };

    // if stereo, flatten to mono
    /* TODO:: If smp is stereo, flatten the tracks
    ~lbuf.loadToFloatArray(action: { arg array;
    var a = array;
    var left = Buffer.loadCollection(s, a.unlace(2).at(0));
    var right = Buffer.loadCollection(s, a.unlace(2).at(1));
    ~lbuf2 = [left, right];
});
    */

    if(nframes <= 0) { nframes = srcbuf.numFrames - srcst };
    destzone = zones[destzoneidx];
    "LiSa: Load '%' to zone %".format(srcsmpl, destzone).postln;
    zonesize = destzone[1] - destzone[0];
    if(nframes >  zonesize) { nframes = zonesize };
    if(zeroFirst) {
      bigbuf.fill(destzone[0], zonesize, 0.0);
    };
    srcbuf.copyData(bigbuf, destzone[0], srcst, nframes);
  }

  // live sample dur seconds into bigbuf
  liveSampleToZone {|inbus=0, destzoneidx, zeroFirst=false, limit=0|
    var destzone, zonesize;
    destzone = zones[destzoneidx];
    zonesize = destzone[1] - destzone[0];
    if(zeroFirst) {
      bigbuf.fill(destzone[0], zonesize, 0.0);
    };
    ^Synth(\sampleFrames, [
      \inbus, inbus, \buf, bigbuf,
      \insertAt, destzone[0], \numframes, zonesize, \preamp, 1.0, \limit, limit
    ]);
  }


  // show the buffer (TODO: combine with info window)
  show {|width=1000, height=200|
    {bigbuf.plot("Big Buf", width@height, -1,1)}.defer
  }

  selectedZone_ {|zoneidx|
    selectedZone = zoneidx;
    "LiSa: Zone Select % (index %)".format(zoneidx+1, zoneidx).warn;
    if(zoneText.notNil) {
      {
        zoneText.string_((zoneidx+1).asString);
        4.do {
          zoneText.background_(Color.rand);
          0.05.wait;
        };
        zoneText.background_(Color.black);
      }.fork(AppClock);

    };
  }

  info {|position|
    var win, top=0, left=100, width=100, height=100;
    var styler, childView;
    top = Window.screenBounds.height - (height*3);
    left = Window.screenBounds.width - left;

    if(win.notNil) {
      if(win.isClosed.not) {
        win.front;
        ^win;
      }
    };

    if(position.notNil) {
      top=position.y; left=position.x;
    };

    win = Window("LiSa", Rect(left,top,width,height));
    styler = GUIStyler(win);

    childView = styler.getView("LiSa", win.view.bounds, gap: 10@10);

    styler.getSizableText(childView, "zone", 100);
    zoneText = styler.getSizableText(childView, selectedZone+1, 100, fontSize: 64, bold: true);

    ^win.alwaysOnTop_(true).front;
  }

}


