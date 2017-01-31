/* eslint-disable */

var webpack = require('webpack');
var path = require('path');
var SystemBellPlugin = require('system-bell-webpack-plugin');

const isProd = process.env.NODE_ENV === 'production';

const entry = {
  app: isProd ? [
      './src/index.jsx'
    ] : [
      'webpack-dev-server/client?http://0.0.0.0:3030', // WebpackDevServer host and port
      'webpack/hot/only-dev-server', // "only" prevents reload on syntax errors
      './src/index.jsx'
    ],
  pub: './src/index-pub.jsx'
}

module.exports = {
  entry: entry,
  devtool: 'source-map',
  output: {
    path: path.join(__dirname, 'dist'),
    filename: "[name].bundle.js",
    publicPath: '/assets/'
  },
  resolve: {
    extensions: ['.js', '.jsx']
  },
  module: {
    rules: [
      {
        test: /\.jsx?$/,
        exclude: /node_modules/,
        use: isProd ? ['babel-loader'] : ['react-hot-loader', 'babel-loader']
      },
      {
        test: /\.scss$/,
        use: ['style-loader', 'css-loader', 'sass-loader']
      },
      {
        test: /\.css$/,
        use: ['style-loader', 'css-loader']
      },
      {
        test: /\.(png|jpg)$/,
        use: [{
          loader: 'url-loader',
          options: { limit: 8192 }
        }]
      },
    ]
  },
  plugins: [
    new webpack.HotModuleReplacementPlugin(),
    new SystemBellPlugin(),
    new webpack.DefinePlugin({
      'process.env': {
        'NODE_ENV': JSON.stringify(process.env.NODE_ENV)
      }
    })
  ]
};
