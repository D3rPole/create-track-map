let map = L.map("map", {
  crs: L.CRS.Minecraft,
  zoomControl: true,
  attributionControl: false,
})

map.createPane("tracks")
map.createPane("blocks")
map.createPane("signals")
map.createPane("trains")
map.createPane("portals")
map.createPane("stations")
map.getPane("tracks").style.zIndex = 300
map.getPane("blocks").style.zIndex = 500
map.getPane("signals").style.zIndex = 600
map.getPane("trains").style.zIndex = 700
map.getPane("portals").style.zIndex = 800
map.getPane("stations").style.zIndex = 800

map.getPane("tooltipPane").style.zIndex = 1000

const lmgr = new LayerManager(map)
const tmgr = new TrainManager(map, lmgr)
const smgr = new StationManager(map, lmgr)

let leftSide = false

fetch("api/config.json")
  .then((resp) => resp.json())
  .then((cfg) => {
    const { layers, view, dimensions } = cfg
    const {
      initial_dimension,
      initial_position,
      initial_zoom,
      max_zoom,
      min_zoom,
      zoom_controls,
      signals_on,
    } = view

    map.setMinZoom(min_zoom)
    map.setMaxZoom(max_zoom)

    lmgr.setLayerConfig(layers)
    lmgr.setDimensionLabels(dimensions)
    lmgr.switchToDimension(initial_dimension)

    const { x: initialX, z: initialZ } = initial_position
    map.setView([initialZ, initialX], initial_zoom)

    if (!zoom_controls) {
      map.zoomControl.remove()
    }

    leftSide = signals_on === "LEFT"

    L.control.coords().addTo(map)

    startMapUpdates()
  })

function startMapUpdates() {
  const dmgr = new DataManager()

  dmgr.onTrackStatus(({ tracks, portals, stations }) => {
    lmgr.clearTracks()
    lmgr.clearPortals()
    lmgr.clearStations()
    smgr.update(stations)

    tracks.forEach((trk) => {
      const path = trk.path
      if (path.length === 4) {
        L.curve(["M", xz(path[0]), "C", xz(path[1]), xz(path[2]), xz(path[3])], {
          className: "track",
          interactive: false,
          pane: "tracks",
        }).addTo(lmgr.layer(trk.dimension, "tracks"))
      } else if (path.length === 2) {
        L.polyline([xz(path[0]), xz(path[1])], {
          className: "track",
          interactive: false,
          pane: "tracks",
        }).addTo(lmgr.layer(trk.dimension, "tracks"))
      }
    })

    stations.forEach((stn) => {
      L.marker(xz(stn.location), {
        icon: stationIcon,
        rotationAngle: stn.angle,
        pane: "stations",
      })
        .bindTooltip(stn.name, {
          className: "station-name",
          direction: "top",
          offset: L.point(0, -12),
          opacity: 0.7,
        })
        .addTo(lmgr.layer(stn.dimension, "stations"))
    })

    portals.forEach((portal) => {
      L.marker(xz(portal.from.location), {
        icon: portalIcon,
        pane: "stations",
      })
        .on("click", (e) => {
          lmgr.switchDimensions(portal.from.dimension, portal.to.dimension)
          map.panTo(xz(portal.to.location))
        })
        .addTo(lmgr.layer(portal.from.dimension, "portals"))
      L.marker(xz(portal.to.location), {
        icon: portalIcon,
        pane: "stations",
      })
        .on("click", (e) => {
          lmgr.switchDimensions(portal.to.dimension, portal.from.dimension)
          map.panTo(xz(portal.from.location))
        })
        .addTo(lmgr.layer(portal.to.dimension, "portals"))
    })
  })

  dmgr.onBlockStatus(({ blocks }) => {
    lmgr.clearBlocks()

    blocks.forEach((block) => {
      if (!block.reserved && !block.occupied) {
        return
      }
      block.segments.forEach(({ dimension, path }) => {
        if (path.length === 4) {
          L.curve(["M", xz(path[0]), "C", xz(path[1]), xz(path[2]), xz(path[3])], {
            className:
              "track " + (block.reserved ? "reserved" : block.occupied ? "occupied" : ""),
            interactive: false,
            pane: "blocks",
          }).addTo(lmgr.layer(dimension, "blocks"))
        } else if (path.length === 2) {
          L.polyline([xz(path[0]), xz(path[1])], {
            className:
              "track " + (block.reserved ? "reserved" : block.occupied ? "occupied" : ""),
            interactive: false,
            pane: "blocks",
          }).addTo(lmgr.layer(dimension, "blocks"))
        }
      })
    })
  })

  dmgr.onSignalStatus(({ signals }) => {
    lmgr.clearSignals()

    signals.forEach((sig) => {
      if (!!sig.forward) {
        let iconType = sig.forward.type === "CROSS_SIGNAL" ? chainSignalIcon : autoSignalIcon
        let marker = L.marker(xz(sig.location), {
          icon: iconType(sig.forward.state.toLowerCase(), leftSide),
          rotationAngle: sig.forward.angle,
          interactive: false,
          pane: "signals",
        }).addTo(lmgr.layer(sig.dimension, "signals"))
      }
      if (!!sig.reverse) {
        let iconType = sig.reverse.type === "CROSS_SIGNAL" ? chainSignalIcon : autoSignalIcon
        let marker = L.marker(xz(sig.location), {
          icon: iconType(sig.reverse.state.toLowerCase(), leftSide),
          rotationAngle: sig.reverse.angle,
          interactive: false,
          pane: "signals",
        }).addTo(lmgr.layer(sig.dimension, "signals"))
      }
    })
  })

  dmgr.onTrainStatus(({ trains }) => {
    lmgr.clearTrains()
    tmgr.update(trains)

    trains.forEach((train) => {
      let leadCar = null
      if (!train.stopped) {
        if (train.backwards) {
          leadCar = train.cars.length - 1
        } else {
          leadCar = 0
        }
      }

      let schedule = ""
      if(train.schedule != null){

        train.currentPath.path.forEach((trk) => {
            const path = trk.path
            if (path.length === 4) {
              L.curve(["M", xz(path[0]), "C", xz(path[1]), xz(path[2]), xz(path[3])], {
                className: "track path",
                interactive: false,
                pane: "trains",
              }).addTo(lmgr.layer(trk.dimension, "trains"))
            } else if (path.length === 2) {
              L.polyline([xz(path[0]), xz(path[1])], {
                className: "track path",
                interactive: false,
                pane: "trains",
              }).addTo(lmgr.layer(trk.dimension, "trains"))
            }
          })

        schedule = "<hr><span class=\"on-schedule\">On schedule</span><br>"
        train.schedule.instructions.forEach((instruction, i) => {
            if(instruction.instructionType == "Destination"){
                schedule += "<span>";
                if(i == train.schedule.currentEntry){
                    schedule += "=> "
                }
                schedule += instruction.stationName + "</span><br>";
            }
        })
      }

      train.cars.forEach((car, i) => {
        if(car.leading){ // lazily solves the missing carriage data (ignore the problem)
            let parts = car.portal
              ? [
                  [car.leading.dimension, [xz(car.leading.location), xz(car.portal.from.location)]],
                  [car.trailing.dimension, [xz(car.portal.to.location), xz(car.trailing.location)]],
                ]
              : [[car.leading.dimension, [xz(car.leading.location), xz(car.trailing.location)]]]

            parts.map(([dim, part]) =>
              L.polyline(part, {
                weight: 12,
                lineCap: "square",
                className: "train" + (leadCar === i ? " lead-car" : ""),
                pane: "trains",
              })
                .bindTooltip(
                  (train.cars.length === 1
                    ? train.name
                    : `${train.name} <span class="car-number">${i + 1}</span>`) + schedule,
                  {
                    className: "train-name",
                    direction: "right",
                    offset: L.point(12, 0),
                    opacity: 0.7,
                  }
                )
                .addTo(lmgr.layer(dim, "trains"))
            )

            if (leadCar === i) {
              let [dim, edge] = train.backwards ? parts[parts.length - 1] : parts[0]
              let [head, tail] = train.backwards ? [edge[1], edge[0]] : [edge[0], edge[1]]
              let angle = 180 + (Math.atan2(tail[0] - head[0], tail[1] - head[1]) * 180) / Math.PI

              L.marker(head, {
                icon: headIcon,
                rotationAngle: angle,
                pane: "trains",
              }).addTo(lmgr.layer(dim, "trains"))
            }
        }
      })
    })

  })
}
