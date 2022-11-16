import React from 'react';
import { connect } from 'react-redux';
import { ConfigProvider } from '@alifd/next';
import LogoImage from './images/logo.svg';
import { changeLanguage } from '../../reducers/locale';

import './index.scss';

@connect(state => ({ ...state.locale }), { changeLanguage })
@ConfigProvider.config
class Header extends React.Component {
  static displayName = 'Header'

  languageSwitching() {
    const { language = 'en-US' } = this.props;
    this.props.changeLanguage(language === 'en-US' ? 'zh-CN' : 'en-US');
  }

  render() {
    const { locale = {} } = this.props;
    const { home, languageSwitchButton } = locale;
    // const BASE_URL = `https://nacos.io/${language.toLocaleLowerCase()}/`;
    const BASE_URL = '.';
    const NAV_MENU = [{
      id: 1,
      title: home,
      link: BASE_URL,
    }];
    return (
      <header className="header-container-primary">
        <a href="/" className="logo" title="Nacos-Sync">
          <img src={LogoImage} className="logo-img" />
        </a>
        <span className="language-switch language-switch-primary" onClick={() => this.languageSwitching()}>
          {languageSwitchButton}
        </span>
        <ul className="nav-menu">
          {
            NAV_MENU.map(item => (
              <li key={item.id}>
                <a href={item.link} target="_blank">{item.title}</a>
              </li>
            ))
          }
        </ul>
      </header>
    );
  }
}

export default Header;
