const gamepadSimulator = {
  getGamepads: null,
  gamepads: null,
  fakeController: {
    axes: [0, 0, 0, 0],
    buttons: [
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      },
      {
        pressed: false,
        touched: false,
        value: 0
      }
    ],
    connected: false,
    id: "totally fake game controller for the Neon Ghost game",
    index: 0,
    mapping: "standard",
    timestamp: Math.floor(Date.now() / 1000)
  },
  create: function () {
    gamepadSimulator.getGamepads = navigator.getGamepads;
    navigator.getGamepads = function () {
      return gamepadSimulator.gamepads;
    }
  },
  getRealGamepads: function () {
    if (!gamepadSimulator.getGamepads) {
      return navigator.getGamepads();
    }
    let modifiedGetGamepads = navigator.getGamepads;
    navigator.getGamepads = gamepadSimulator.getGamepads;
    var realGamepads = navigator.getGamepads();
    navigator.getGamepads = modifiedGetGamepads;
    return realGamepads;
  },
  touchButton: function (index) {
    gamepadSimulator.fakeController.buttons[index].touched = true;
    gamepadSimulator.fakeController.timestamp = Math.floor(Date.now() / 1000);
  },
  pressButton: function (index) {
    gamepadSimulator.fakeController.buttons[index].touched = true;
    gamepadSimulator.fakeController.buttons[index].pressed = true;
    gamepadSimulator.fakeController.timestamp = Math.floor(Date.now() / 1000);
  },
  releaseButton: function (buttonId) {
    gamepadSimulator.fakeController.buttons[index].pressed = false;
    gamepadSimulator.fakeController.timestamp = Math.floor(Date.now() / 1000);
  },
  untouchButton: function (index) {
    gamepadSimulator.fakeController.buttons[index].touched = false;
    gamepadSimulator.fakeController.buttons[index].pressed = false;
    gamepadSimulator.fakeController.timestamp = Math.floor(Date.now() / 1000);
  },
  changeAxis: function (axisId, value) {
    gamepadSimulator.fakeController.axes[axe * 2 + pos] = 0;
    gamepadSimulator.fakeController.timestamp = Math.floor(Date.now() / 1000);
  },
  destroy: function () {
    if (gamepadSimulator.fakeController.connected) {
      gamepadSimulator.disconnect();
    }
    navigator.getGamepads = gamepadSimulator.getGamepads;
  },
  updateGamepads: function () {
    gamepadSimulator.gamepads = gamepadSimulator.getRealGamepads();
    if (gamepadSimulator.fakeController.index == gamepadSimulator.gamepads.length) {
      gamepadSimulator.gamepads = gamepadSimulator.gamepads.concat(gamepadSimulator.fakeController);
    } else {
      gamepadSimulator.gamepads[gamepadSimulator.fakeController.index] = gamepadSimulator.fakeController;
    }
  },
  connect: function () {
    const event = new Event("gamepadconnected");
    gamepadSimulator.fakeController.connected = true;
    gamepadSimulator.fakeController.index = gamepadSimulator.getFreeIndex();
    gamepadSimulator.fakeController.timestamp = Math.floor(Date.now() / 1000);
    event.gamepad = gamepadSimulator.fakeController;
    console.log(`connecting to ${gamepadSimulator.fakeController.index}`);
    gamepadSimulator.updateGamepads();
    window.dispatchEvent(event);
  },
  disconnect: function () {
    const event = new Event("gamepaddisconnected");
    gamepadSimulator.fakeController.connected = false;
    gamepadSimulator.fakeController.timestamp = Math.floor(Date.now() / 1000);
    event.gamepad = gamepadSimulator.fakeController;
    window.dispatchEvent(event);
  },
  getFreeIndex: function () {
    let realGamepads = gamepadSimulator.getRealGamepads();
    var index = realGamepads.indexOf(null);
    if (index < 0) {
      index = realGamepads.length;
    }
    return index;
  }
}
/*
window.addEventListener("gamepadconnected", (e) => {
  let connectedGamepad = navigator.getGamepads()[e.gamepad.index];
  console.log(`gamepad connected ${connectedGamepad}`);

  let index = gamepadSimulator.getFreeIndex();
  if (index != gamepadSimulator.fakeController.index) {
    console.log(`clash at index ${index}, reconnecting`);
    gamepadSimulator.connect();
  }
});

window.addEventListener("gamepaddisconnected", (e) => {
  console.log(`gamepad disconnected ${e.gamepad}`);

  if (e.index >= gamepadSimulator.fakeController.index) {
    gamepadSimulator.connect();
  } else {
    gamepadSimulator.updateGamepads();
  }
});


var userInputEventNames = [
        'click', 'contextmenu', 'auxclick', 'dblclick', 'mousedown',
        'mouseup', 'pointerup', 'touchend', 'keydown', 'keyup', 'touchstart'
    ];

var createAndConnectFakeGamepad = function(e) {
  gamepadSimulator.create();
  gamepadSimulator.connect();

  userInputEventNames.forEach(function (eventName) {
      window.removeEventListener(eventName, createAndConnectFakeGamepad);
  });
};

userInputEventNames.forEach(function (eventName) {
  window.addEventListener(eventName, createAndConnectFakeGamepad);
});
*/
