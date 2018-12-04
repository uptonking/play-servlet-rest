import React from 'react';
import ReactDOM from 'react-dom';
import {applyMiddleware, compose, createStore} from 'redux';
import {Provider} from 'react-redux';
import {routerMiddleware, connectRouter} from 'connected-react-router';
import {createBrowserHistory} from 'history';
import createSagaMiddleware from 'redux-saga';

import rootReducer from './reducer';
import App from './App';
// import { AppContainer } from 'react-hot-loader'

const history = createBrowserHistory();
const sagaMiddleware = createSagaMiddleware();

// const composeEnhancer = window.__REDUX_DEVTOOLS_EXTENSION_COMPOSE__ || compose;
const composeEnhancer = compose;

const store = createStore(
    // connectRouter(history)(rootReducer),
    rootReducer(history),
    composeEnhancer(
        applyMiddleware(
            routerMiddleware(history),
            sagaMiddleware,
        ),
    ),
);

ReactDOM.render((
    <Provider store={store}>
        <App history={history}/>
    </Provider>
), document.getElementById('root'));
