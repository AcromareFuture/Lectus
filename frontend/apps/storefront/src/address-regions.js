import { areaList } from '@vant/area-data';

const entries = list => Object.entries(list).map(([code, name]) => ({ code, name }));
export const provinces = entries(areaList.province_list);
export function regionOptions(value = {}) {
  const province = provinces.find(item => item.name === value.province);
  const cities = province ? entries(areaList.city_list).filter(item => item.code.startsWith(province.code.slice(0, 2))) : [];
  const city = cities.find(item => item.name === value.city);
  const districts = city ? entries(areaList.county_list).filter(item => item.code.startsWith(city.code.slice(0, 4))) : [];
  return { cities, districts };
}
export function validRegion(value) {
  const { cities, districts } = regionOptions(value);
  return cities.some(item => item.name === value.city) && districts.some(item => item.name === value.district);
}
export function deliveryAddress(value) {
  if (!validRegion(value) || !value.detail?.trim()) throw new Error('请选择完整的省市区，并填写详细地址。');
  return [value.province, value.city, value.district, value.detail.trim()].join(' ');
}
