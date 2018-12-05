import * as C from '../action/actionConstant';
import { mockWorkbookDefaultData } from "../util/mock-data";

const initialState = {
    settings: {
        data: mockWorkbookDefaultData(),
        colHeaders: true,
        rowHeaders: true,
        readOnly: false,
        colWidths: 100,
        minSpareRows: 160,
        minSpareCols: 26,

        // rowHeights: 23,
        // stretchH: 'all',
    },
};

// Action reducers for callbacks triggered by Handsontable
const workbookReducer = (state = initialState, action) => {

    switch (action.type) {

        case C.UPDATE_SHEET_DATA:
            const newData = state.data.slice(0);

            for (let [row, column, oldValue, newValue] of action.dataChanges) {
                newData[row][column] = newValue;
            }


            const newSettings1 = { ...state.settings, data: newData };
            return { ...state, settings: newSettings1 };

        case C.UPDATE_SHEET_READ_ONLY:
            const newSettings2 = { ...state.settings, readOnly: action.readOnly };
            return { ...state, settings: newSettings2 };


        default:
            return state;
    }
};

export default workbookReducer;