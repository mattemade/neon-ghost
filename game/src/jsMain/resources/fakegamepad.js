const gamepadSimulator = {
  getGamepads: null,
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
    id: "Standard gamepad by Alvaro Montoro",
    index: 0,
    mapping: "standard",
    timestamp: Math.floor(Date.now() / 1000)
  },
  create: function () {
    gamepadSimulator.getGamepads = navigator.getGamepads;
    navigator.getGamepads = function () {
      return gamepadSimulator.getGamepads().concat(gamepadSimulator.fakeController);
    }
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
  connect: function () {
    const event = new Event("gamepadconnected");
    gamepadSimulator.fakeController.connected = true;
    gamepadSimulator.fakeController.timestamp = Math.floor(Date.now() / 1000);
    event.gamepad = gamepadSimulator.fakeController;
    window.dispatchEvent(event);
  },
  disconnect: function () {
    const event = new Event("gamepaddisconnected");
    gamepadSimulator.fakeController.connected = false;
    gamepadSimulator.fakeController.timestamp = Math.floor(Date.now() / 1000);
    event.gamepad = gamepadSimulator.fakeController;
    window.dispatchEvent(event);
  }
}

gamepadSimulator.create();
