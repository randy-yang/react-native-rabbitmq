import {NativeModules, DeviceEventEmitter} from 'react-native';

const RabbitMqConnection = NativeModules.RabbitMqConnection;

export class Connection {
    
    constructor(config) {
        this.rabbitmqconnection = RabbitMqConnection;
        this.callbacks = {};
        
        this.connected = false;

        this.listener = DeviceEventEmitter.addListener('RabbitMqConnectionEvent', this.handleEvent.bind(this));

        this.rabbitmqconnection.initialize(config);
    }
    
    connect() {
        this.rabbitmqconnection.connect();
    }    
    
    close() {
        this.rabbitmqconnection.close();
        this.listener.remove();
    }

    handleEvent(event) {
        if (this.callbacks.hasOwnProperty(event.name)) {
            this.callbacks[event.name](event)
        }
        
        if (event.name === 'connected') {
          this.connected = true;
        }
    }
    
    on(event, callback) {
        this.callbacks[event] = callback;
    } 

    removeon(event) {
        delete this.callbacks[event];
    }
}

export default Connection;